package com.orbit.domain;

import static com.orbit.domain.CollaborationModels.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final JdbcTemplate jdbc;
    private static final String VISIBLE = """
            FROM user_notification n JOIN workspace w ON w.id=n.workspace_id
            JOIN workspace_member m ON m.workspace_id=n.workspace_id AND m.user_id=n.user_id
            WHERE n.user_id=?
            """;

    public NotificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Inbox inbox(String actor, int page, int size) {
        DomainService.validatePage(page, size);
        long total = jdbc.queryForObject("SELECT COUNT(*) " + VISIBLE, Long.class, actor);
        long unread = jdbc.queryForObject("SELECT COUNT(*) " + VISIBLE + " AND n.read_at IS NULL", Long.class, actor);
        List<Notification> items = jdbc.query("SELECT n.*,w.name AS workspace_name " + VISIBLE
                + " ORDER BY n.created_at DESC,n.id LIMIT ? OFFSET ?", (row, index) -> new Notification(
                        row.getString("id"), row.getString("workspace_id"), row.getString("workspace_name"),
                        row.getString("task_id"), row.getString("type"), row.getString("title"), row.getString("body"),
                        row.getObject("read_at") != null, row.getObject("created_at", OffsetDateTime.class)), actor, size, (long) page * size);
        return new Inbox(items, page, size, total, (total + size - 1) / size, unread);
    }

    @Transactional
    public void read(String actor, String id) {
        if (jdbc.queryForObject("SELECT COUNT(*) " + VISIBLE + " AND n.id=?", Long.class, actor, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found.");
        }
        jdbc.update("UPDATE user_notification SET read_at=COALESCE(read_at,?) WHERE id=? AND user_id=?", now(), id, actor);
    }

    @Transactional
    public void readAll(String actor) {
        jdbc.update("""
                UPDATE user_notification SET read_at=? WHERE user_id=? AND read_at IS NULL
                AND workspace_id IN (SELECT workspace_id FROM workspace_member WHERE user_id=?)
                """, now(), actor, actor);
    }

    @Transactional
    public void assigned(String workspace, String task, String actor, String recipient, String title) {
        if (recipient != null) notify(workspace, task, actor, recipient, "ASSIGNED", "You were assigned a task", title);
    }

    @Transactional
    public void commented(String workspace, String task, String actor, String assignee, String title, String body) {
        var recipients = new HashSet<>(jdbc.query("SELECT DISTINCT author_id FROM task_comment WHERE workspace_id=? AND task_id=?",
                (row, index) -> row.getString(1), workspace, task));
        if (assignee != null) recipients.add(assignee);
        String snippet = body.strip();
        if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "…";
        for (String recipient : recipients) {
            notify(workspace, task, actor, recipient, "COMMENT", "New comment on " + title, snippet);
        }
    }

    @Transactional
    public void membership(String workspace, String actor, String recipient, String body) {
        notify(workspace, null, actor, recipient, "MEMBERSHIP", "Your workspace access changed", body);
    }

    private void notify(String workspace, String task, String actor, String recipient, String type, String title, String body) {
        if (actor.equals(recipient)) return;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM workspace_member WHERE workspace_id=? AND user_id=?", Long.class, workspace, recipient) == 0) return;
        jdbc.update("""
                INSERT INTO user_notification(id,user_id,workspace_id,task_id,type,title,body,created_at)
                VALUES(?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), recipient, workspace, task, type, limit(title, 200), limit(body, 500), now());
    }

    private static String limit(String value, int size) { return value.length() <= size ? value : value.substring(0, size); }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
