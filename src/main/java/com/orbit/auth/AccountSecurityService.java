package com.orbit.auth;

import com.orbit.mail.MailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Account locks serialize token issuance/consumption, password changes, and session revocation. */
@Service
public class AccountSecurityService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AccountProperties properties;
    private final MailService mail;

    public AccountSecurityService(JdbcTemplate jdbc, PasswordEncoder encoder, AccountProperties properties, MailService mail) {
        this.jdbc = jdbc; this.encoder = encoder; this.properties = properties; this.mail = mail;
    }

    private record Account(String id, String email, String name, String hash, long version, boolean verified) {}
    public record SessionView(String id, Instant createdAt, Instant lastAccessedAt, Instant expiresAt, boolean current) {}

    @Transactional
    public CurrentUser.UserView register(String name, String email, String password) {
        if (!properties.isRegistrationEnabled()) throw status(HttpStatus.FORBIDDEN,"Registration is currently disabled.");
        validatePassword(password);
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        String normalizedName = name.strip();
        if (normalizedName.isEmpty()) throw status(HttpStatus.BAD_REQUEST,"Name is required.");
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO app_user(id,email,display_name,password_hash,created_at,email_verified,auth_version) VALUES(?,?,?,?,?,FALSE,0)",
                    id, normalizedEmail, normalizedName, encoder.encode(password), now());
        } catch (DuplicateKeyException ex) {
            throw status(HttpStatus.CONFLICT,"An account with this email already exists.");
        }
        issue(new Account(id,normalizedEmail,normalizedName,"",0,false),"VERIFY_EMAIL",properties.getVerificationTokenTtl());
        event(id,"REGISTERED");
        return new CurrentUser.UserView(id,normalizedName,normalizedEmail,false);
    }

    @Transactional
    public CurrentUser.UserView updateProfile(AccountPrincipal principal, String name) {
        Account account = lockAuthenticated(principal);
        String normalized = name.strip();
        jdbc.update("UPDATE app_user SET display_name=? WHERE id=?",normalized,account.id());
        event(account.id(),"PROFILE_UPDATED");
        return new CurrentUser.UserView(account.id(),normalized,account.email(),account.verified());
    }

    @Transactional
    public void changePassword(AccountPrincipal principal, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        Account account = lockAuthenticated(principal);
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72 || !encoder.matches(currentPassword,account.hash()))
            throw status(HttpStatus.BAD_REQUEST,"Current password is incorrect.");
        replacePassword(account,newPassword,"PASSWORD_CHANGED");
    }

    @Transactional
    public void forgotPassword(String email) {
        Account account = findByEmail(email);
        if (account == null) return;
        account = lock(account.id());
        issue(account,"RESET_PASSWORD",properties.getResetTokenTtl());
    }

    @Transactional
    public void resendVerification(String email) {
        Account account = findByEmail(email);
        if (account == null) return;
        account = lock(account.id());
        if (!account.verified()) issue(account,"VERIFY_EMAIL",properties.getVerificationTokenTtl());
    }

    @Transactional
    public void resendVerification(AccountPrincipal principal) {
        Account account = lockAuthenticated(principal);
        if (!account.verified()) issue(account,"VERIFY_EMAIL",properties.getVerificationTokenTtl());
    }

    @Transactional
    public void resetPassword(String token, String password) {
        validatePassword(password);
        Account account = consume(token,"RESET_PASSWORD");
        replacePassword(account,password,"PASSWORD_RESET");
    }

    @Transactional
    public void verifyEmail(String token) {
        Account account = consume(token,"VERIFY_EMAIL");
        jdbc.update("UPDATE app_user SET email_verified=TRUE WHERE id=?",account.id());
        invalidateTokens(account.id(),"VERIFY_EMAIL");
        event(account.id(),"EMAIL_VERIFIED");
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(AccountPrincipal principal, String currentSessionId) {
        authenticate(principal);
        return jdbc.query("SELECT PRIMARY_ID,SESSION_ID,CREATION_TIME,LAST_ACCESS_TIME,EXPIRY_TIME FROM SPRING_SESSION WHERE PRINCIPAL_NAME=? AND EXPIRY_TIME>? ORDER BY LAST_ACCESS_TIME DESC,PRIMARY_ID LIMIT 100",
                (r,n) -> new SessionView(r.getString("PRIMARY_ID"),Instant.ofEpochMilli(r.getLong("CREATION_TIME")),
                        Instant.ofEpochMilli(r.getLong("LAST_ACCESS_TIME")),Instant.ofEpochMilli(r.getLong("EXPIRY_TIME")),r.getString("SESSION_ID").equals(currentSessionId)),
                principal.getUsername(),System.currentTimeMillis());
    }

    @Transactional
    public boolean revokeSession(AccountPrincipal principal, String id, String currentSessionId) {
        Account account = lockAuthenticated(principal);
        List<String> rows = jdbc.query("SELECT SESSION_ID FROM SPRING_SESSION WHERE PRIMARY_ID=? AND PRINCIPAL_NAME=?",(r,n) -> r.getString(1),id,account.email());
        if (rows.isEmpty()) throw status(HttpStatus.NOT_FOUND,"Session not found.");
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRIMARY_ID=? AND PRINCIPAL_NAME=?",id,account.email());
        event(account.id(),"SESSION_REVOKED");
        return rows.get(0).equals(currentSessionId);
    }

    private Account consume(String rawToken, String purpose) {
        String hash = hash(rawToken);
        List<String> users = jdbc.query("SELECT user_id FROM account_token WHERE token_hash=? AND purpose=?",(r,n) -> r.getString(1),hash,purpose);
        if (users.isEmpty()) throw invalidToken();
        Account account = lock(users.get(0));
        int consumed = jdbc.update("UPDATE account_token SET consumed_at=? WHERE token_hash=? AND user_id=? AND purpose=? AND consumed_at IS NULL AND expires_at>?",
                now(),hash,account.id(),purpose,now());
        if (consumed != 1) throw invalidToken();
        return account;
    }

    private void issue(Account account, String purpose, Duration ttl) {
        // Issuance runs while the account row is locked (or inserted by this transaction).
        // Do not let anonymous repeated requests invalidate a usable link or flood an inbox.
        Long recent = jdbc.queryForObject("SELECT COUNT(*) FROM account_token WHERE user_id=? AND purpose=? AND created_at>?",
                Long.class,account.id(),purpose,now().minus(properties.getTokenSendCooldown()));
        if (recent != null && recent > 0) return;
        invalidateTokens(account.id(),purpose);
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime now = now();
        jdbc.update("INSERT INTO account_token(id,user_id,purpose,token_hash,expires_at,created_at) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(),account.id(),purpose,hash(token),now.plus(ttl),now);
        boolean reset = "RESET_PASSWORD".equals(purpose);
        String url = properties.getPublicBaseUrl() + (reset ? "/#reset-password?token=" : "/#verify-email?token=") + token;
        String subject = reset ? "Reset your Orbit password" : "Verify your Orbit email address";
        String body = reset ? "A password reset was requested for your Orbit account. Open this link to choose a new password:\n\n"
                : "Welcome to Orbit. Verify your email address by opening this link:\n\n";
        body += url + "\n\nThis link expires in " + ttl.toMinutes() + " minutes and can be used once. If you did not request this, you can ignore this email.\n\nOrbit";
        mail.enqueueAction(account.email(),subject,body,"ACCOUNT",hash(token),now.plus(ttl));
        event(account.id(),reset ? "PASSWORD_RESET_REQUESTED" : "VERIFICATION_REQUESTED");
    }

    private void replacePassword(Account account, String password, String event) {
        jdbc.update("UPDATE app_user SET password_hash=?,auth_version=auth_version+1,password_changed_at=? WHERE id=?",encoder.encode(password),now(),account.id());
        invalidateTokens(account.id(),"RESET_PASSWORD");
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?",account.email());
        event(account.id(),event);
        mail.enqueue(account.email(),"Your Orbit password was changed","Your Orbit account password was changed. All existing sessions have been signed out. If this was not you, request a password reset immediately.\n\nOrbit");
    }

    private void invalidateTokens(String user, String purpose) {
        jdbc.update("UPDATE account_token SET consumed_at=? WHERE user_id=? AND purpose=? AND consumed_at IS NULL",now(),user,purpose);
    }

    private Account lockAuthenticated(AccountPrincipal principal) {
        Account account = lock(principal.id());
        if (account.version() != principal.authVersion()) throw status(HttpStatus.UNAUTHORIZED,"Your session has been revoked. Sign in again.");
        if (properties.isEmailVerificationRequired() && !account.verified()) throw status(HttpStatus.FORBIDDEN,"Verify your email address before continuing.");
        return account;
    }

    private void authenticate(AccountPrincipal principal) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND auth_version=?",Long.class,principal.id(),principal.authVersion());
        if (count == null || count != 1) throw status(HttpStatus.UNAUTHORIZED,"Your session has been revoked. Sign in again.");
    }

    private Account lock(String user) {
        return jdbc.query("SELECT id,email,display_name,password_hash,auth_version,email_verified FROM app_user WHERE id=? FOR UPDATE",(r,n) ->
                new Account(r.getString("id"),r.getString("email"),r.getString("display_name"),r.getString("password_hash"),r.getLong("auth_version"),r.getBoolean("email_verified")),user)
                .stream().findFirst().orElseThrow(() -> status(HttpStatus.UNAUTHORIZED,"Account is unavailable."));
    }

    private Account findByEmail(String email) {
        return jdbc.query("SELECT id,email,display_name,password_hash,auth_version,email_verified FROM app_user WHERE email=?",(r,n) ->
                new Account(r.getString("id"),r.getString("email"),r.getString("display_name"),r.getString("password_hash"),r.getLong("auth_version"),r.getBoolean("email_verified")),email.strip().toLowerCase(Locale.ROOT))
                .stream().findFirst().orElse(null);
    }

    private void event(String user, String type) {
        jdbc.update("INSERT INTO account_security_event(id,user_id,event_type,occurred_at) VALUES(?,?,?,?)",UUID.randomUUID().toString(),user,type,now());
    }

    public static void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw status(HttpStatus.BAD_REQUEST,"Password must contain at least 12 characters and fit within 72 UTF-8 bytes.");
    }

    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable.",impossible); }
    }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private static ResponseStatusException invalidToken() { return status(HttpStatus.BAD_REQUEST,"This link is invalid, expired, or already used. Request a new link."); }
    private static ResponseStatusException status(HttpStatus status, String reason) { return new ResponseStatusException(status,reason); }
}
