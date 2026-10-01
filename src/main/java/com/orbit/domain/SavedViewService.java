package com.orbit.domain;

import static com.orbit.domain.ProductivityModels.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Personal preferences remain private even to workspace owners. */
@Service
@Transactional(readOnly = true)
public class SavedViewService {
    private final JdbcTemplate jdbc;
    private final DomainService domain;
    private static final RowMapper<SavedView> ROW = (row, index) -> new SavedView(
            row.getString("id"), row.getString("workspace_id"), row.getString("label"), row.getString("query_text"),
            row.getString("status") == null ? null : DomainModels.TaskStatus.valueOf(row.getString("status")),
            row.getString("priority") == null ? null : DomainModels.Priority.valueOf(row.getString("priority")),
            row.getString("project_id"), row.getString("assignee_id"), row.getLong("version"),
            row.getObject("created_at", OffsetDateTime.class), row.getObject("updated_at", OffsetDateTime.class));

    public SavedViewService(JdbcTemplate jdbc, DomainService domain) { this.jdbc = jdbc; this.domain = domain; }

    public List<SavedView> list(String workspace, String actor) {
        domain.requireMember(workspace, actor);
        return jdbc.query("SELECT * FROM saved_task_view WHERE workspace_id=? AND user_id=? ORDER BY updated_at DESC,id LIMIT 100",
                ROW, workspace, actor);
    }

    public SavedView get(String workspace, String actor, String id) {
        domain.requireMember(workspace, actor);
        return jdbc.query("SELECT * FROM saved_task_view WHERE workspace_id=? AND user_id=? AND id=?", ROW, workspace, actor, id)
                .stream().findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Saved view not found."));
    }

    @Transactional
    public SavedView create(String workspace, String actor, CreateView input) {
        String label = label(input.label());
        // The workspace lock serializes the per-member quota and membership changes.
        domain.lockWorkspace(workspace, actor);
        domain.validateFilterReferences(workspace, input.projectId(), input.assigneeId());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM saved_task_view WHERE workspace_id=? AND user_id=?", Long.class, workspace, actor) >= 100)
            throw error(HttpStatus.CONFLICT, "You can save up to 100 views in a workspace. Remove a view before adding another.");
        String id = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO saved_task_view(id,workspace_id,user_id,label,query_text,status,priority,project_id,assignee_id,version,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,0,?,?)
                """, id, workspace, actor, label, query(input.q()), enumName(input.status()), enumName(input.priority()),
                input.projectId(), input.assigneeId(), now, now);
        return get(workspace, actor, id);
    }

    @Transactional
    public SavedView update(String workspace, String actor, String id, UpdateView input) {
        String label = label(input.label());
        domain.lockWorkspace(workspace, actor);
        SavedView before = get(workspace, actor, id);
        if (before.version() != input.version()) throw error(HttpStatus.CONFLICT, "This saved view has changed. Refresh before saving.");
        domain.validateFilterReferences(workspace, input.projectId(), input.assigneeId());
        int changed = jdbc.update("""
                UPDATE saved_task_view SET label=?,query_text=?,status=?,priority=?,project_id=?,assignee_id=?,version=version+1,updated_at=?
                WHERE workspace_id=? AND user_id=? AND id=? AND version=?
                """, label, query(input.q()), enumName(input.status()), enumName(input.priority()),
                input.projectId(), input.assigneeId(), OffsetDateTime.now(ZoneOffset.UTC), workspace, actor, id, input.version());
        if (changed != 1) throw error(HttpStatus.CONFLICT, "This saved view has changed. Refresh before saving.");
        return get(workspace, actor, id);
    }

    @Transactional
    public void delete(String workspace, String actor, String id) {
        domain.lockWorkspace(workspace, actor);
        if (jdbc.update("DELETE FROM saved_task_view WHERE workspace_id=? AND user_id=? AND id=?", workspace, actor, id) != 1)
            throw error(HttpStatus.NOT_FOUND, "Saved view not found.");
    }

    private static String enumName(Enum<?> value) { return value == null ? null : value.name(); }
    private static String label(String value) {
        if (value == null || value.strip().isEmpty() || value.length() > 80 || value.indexOf('\0') >= 0)
            throw error(HttpStatus.BAD_REQUEST, "A saved view label must contain text and be at most 80 characters.");
        return value.strip();
    }
    private static String query(String value) { return value == null || value.isEmpty() ? null : value; }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
