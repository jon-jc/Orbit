package com.orbit.domain;

import static com.orbit.domain.CollaborationModels.*;
import static com.orbit.domain.DomainModels.*;

import com.orbit.auth.AccountProperties;
import com.orbit.mail.MailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CollaborationService {
    private final JdbcTemplate jdbc;
    private final MailService mail;
    private final AccountProperties accounts;
    private final boolean mailEnabled;
    private final SecureRandom random = new SecureRandom();

    public CollaborationService(JdbcTemplate jdbc, MailService mail, AccountProperties accounts,
            @Value("${orbit.mail.enabled:false}") boolean mailEnabled) {
        this.jdbc = jdbc; this.mail = mail; this.accounts = accounts; this.mailEnabled = mailEnabled;
    }

    public Settings settings(String workspace, String actor) {
        Role role = role(workspace, actor);
        return jdbc.queryForObject("SELECT id,name,version,created_at FROM workspace WHERE id=?",
                (row, index) -> new Settings(row.getString(1), row.getString(2), row.getLong(3), role,
                        row.getObject(4, OffsetDateTime.class)), workspace);
    }

    @Transactional
    public Settings rename(String workspace, String actor, Rename input) {
        lockOwner(workspace, actor);
        int changed = jdbc.update("UPDATE workspace SET name=?,version=version+1 WHERE id=? AND version=?",
                input.name().strip(), workspace, input.version());
        if (changed != 1) throw error(HttpStatus.CONFLICT, "Workspace settings have changed. Refresh before saving.");
        audit(workspace, actor, "renamed", "workspace", input.name().strip());
        return settings(workspace, actor);
    }

    public List<Invitation> invitations(String workspace, String actor) {
        owner(workspace, actor);
        return jdbc.query("SELECT * FROM workspace_invitation WHERE workspace_id=? ORDER BY created_at DESC,id LIMIT 100",
                (row, index) -> new Invitation(row.getString("id"), row.getString("email"), Role.valueOf(row.getString("role")),
                        row.getObject("accepted_at") != null ? "ACCEPTED" : row.getObject("revoked_at") != null ? "REVOKED"
                        : row.getObject("expires_at", OffsetDateTime.class).isBefore(now()) ? "EXPIRED" : "PENDING",
                        row.getObject("expires_at", OffsetDateTime.class), row.getObject("created_at", OffsetDateTime.class)), workspace);
    }

    @Transactional
    public Invitation invite(String workspace, String actor, Invite input) {
        lockOwner(workspace, actor);
        if (!mailEnabled) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery must be configured before sending invitations.");
        if (input.role() == Role.OWNER) throw error(HttpStatus.BAD_REQUEST, "Invite as a member or viewer; promote after joining.");
        String email = input.email().strip().toLowerCase(Locale.ROOT);
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM workspace_member m JOIN app_user u ON u.id=m.user_id
                WHERE m.workspace_id=? AND u.email=?
                """, Long.class, workspace, email) > 0) throw error(HttpStatus.CONFLICT, "This account is already a workspace member.");
        jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", String.class, actor);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM workspace_invitation WHERE invited_by=? AND created_at>?", Long.class,
                actor, now().minusDays(1)) >= 100) throw error(HttpStatus.TOO_MANY_REQUESTS, "Daily invitation limit reached. Try again tomorrow.");
        OffsetDateTime now = now();
        jdbc.update("""
                UPDATE workspace_invitation SET revoked_at=? WHERE workspace_id=? AND email=?
                AND accepted_at IS NULL AND revoked_at IS NULL
                """, now, workspace, email);
        byte[] secret = new byte[32]; random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String id = UUID.randomUUID().toString();
        OffsetDateTime expires = now.plusDays(7);
        jdbc.update("""
                INSERT INTO workspace_invitation(id,workspace_id,email,role,token_hash,invited_by,created_at,expires_at)
                VALUES(?,?,?,?,?,?,?,?)
                """, id, workspace, email, input.role().name(), hash(token), actor, now, expires);
        String name = settings(workspace, actor).name();
        mail.enqueueAction(email, "You're invited to an Orbit workspace", "You have been invited to " + name
                + " as a " + input.role().name().toLowerCase(Locale.ROOT) + ".\n\n"
                + "Sign in or create an account with " + email + ", then accept this invitation:\n"
                + accounts.getPublicBaseUrl() + "/#invite?token=" + token
                + "\n\nThis invitation expires in seven days. If you were not expecting it, you may ignore it.",
                "INVITATION", hash(token), expires);
        audit(workspace, actor, "invited", "member", email);
        return new Invitation(id, email, input.role(), "PENDING", expires, now);
    }

    @Transactional
    public void revoke(String workspace, String actor, String id) {
        lockOwner(workspace, actor);
        List<String> recipients = jdbc.query("SELECT email FROM workspace_invitation WHERE workspace_id=? AND id=?",
                (row, index) -> row.getString(1), workspace, id);
        if (recipients.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Invitation not found.");
        int changed = jdbc.update("""
                UPDATE workspace_invitation SET revoked_at=? WHERE workspace_id=? AND id=?
                AND accepted_at IS NULL AND revoked_at IS NULL
                """, now(), workspace, id);
        if (changed == 0) throw error(HttpStatus.CONFLICT, "This invitation is already used or revoked.");
        audit(workspace, actor, "revoked invitation for", "member", recipients.get(0));
    }

    public Preview preview(String token) {
        Pending invitation = valid(token);
        return new Preview(invitation.workspaceName(), invitation.email(), invitation.role(), invitation.expiresAt());
    }

    @Transactional
    public Workspace accept(String actor, String token) {
        Pending invitation = valid(token);
        jdbc.queryForObject("SELECT id FROM workspace WHERE id=? FOR UPDATE", String.class, invitation.workspace());
        invitation = valid(token);
        String email = jdbc.queryForObject("SELECT email FROM app_user WHERE id=?", String.class, actor);
        if (!invitation.email().equals(email)) throw error(HttpStatus.FORBIDDEN, "Sign in with the invited email address.");
        int used = jdbc.update("""
                UPDATE workspace_invitation SET accepted_at=? WHERE id=? AND accepted_at IS NULL
                AND revoked_at IS NULL AND expires_at>?
                """, now(), invitation.id(), now());
        if (used != 1) throw error(HttpStatus.CONFLICT, "This invitation is no longer available.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM workspace_member WHERE workspace_id=? AND user_id=?", Long.class,
                invitation.workspace(), actor) == 0) {
            jdbc.update("INSERT INTO workspace_member(workspace_id,user_id,role,created_at) VALUES(?,?,?,?)",
                    invitation.workspace(), actor, invitation.role().name(), now());
            audit(invitation.workspace(), actor, "accepted invitation to", "workspace", invitation.workspaceName());
        }
        Settings result = settings(invitation.workspace(), actor);
        return new Workspace(result.id(), result.name(), result.role(), result.createdAt());
    }

    private Pending valid(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw error(HttpStatus.NOT_FOUND, "Invitation is unavailable or expired.");
        List<Pending> matches = jdbc.query("""
                SELECT i.*,w.name AS workspace_name FROM workspace_invitation i JOIN workspace w ON w.id=i.workspace_id
                JOIN workspace_member m ON m.workspace_id=i.workspace_id AND m.user_id=i.invited_by AND m.role='OWNER'
                WHERE i.token_hash=? AND i.accepted_at IS NULL AND i.revoked_at IS NULL AND i.expires_at>?
                """, (row, index) -> new Pending(row.getString("id"), row.getString("workspace_id"), row.getString("workspace_name"),
                        row.getString("email"), Role.valueOf(row.getString("role")), row.getObject("expires_at", OffsetDateTime.class)), hash(token), now());
        if (matches.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Invitation is unavailable or expired.");
        return matches.get(0);
    }

    private Role role(String workspace, String actor) {
        return jdbc.query("SELECT role FROM workspace_member WHERE workspace_id=? AND user_id=?",
                (row, index) -> Role.valueOf(row.getString(1)), workspace, actor).stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Workspace not found."));
    }

    private void owner(String workspace, String actor) {
        if (role(workspace, actor) != Role.OWNER) throw error(HttpStatus.FORBIDDEN, "Only workspace owners can change settings or invitations.");
    }

    private void lockOwner(String workspace, String actor) {
        owner(workspace, actor);
        jdbc.queryForObject("SELECT id FROM workspace WHERE id=? FOR UPDATE", String.class, workspace);
        owner(workspace, actor);
    }

    private void audit(String workspace, String actor, String action, String type, String name) {
        jdbc.update("INSERT INTO activity_event(id,workspace_id,actor_id,action,entity_type,entity_name,created_at) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), workspace, actor, action, type, name, now());
    }

    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private record Pending(String id, String workspace, String workspaceName, String email, Role role, OffsetDateTime expiresAt) { }
}
