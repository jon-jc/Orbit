package com.orbit.domain;

import static com.orbit.domain.DomainModels.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Workspace-scoped persistence and policy. All writes and their audit events commit together. */
@Service
@Transactional(readOnly = true)
public class DomainService {
    private final JdbcTemplate jdbc;

    public DomainService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String PROJECT_SELECT = """
            SELECT p.*, (SELECT COUNT(*) FROM task t WHERE t.workspace_id=p.workspace_id AND t.project_id=p.id) AS task_count,
            (SELECT COUNT(*) FROM task t WHERE t.workspace_id=p.workspace_id AND t.project_id=p.id AND t.status='DONE') AS completed_count
            FROM project p
            """;
    private static final String TASK_SELECT = """
            SELECT t.*, p.name AS project_name, p.color AS project_color, u.display_name AS assignee_name,
            (SELECT COUNT(*) FROM task_comment c WHERE c.workspace_id=t.workspace_id AND c.task_id=t.id) AS comment_count
            FROM task t JOIN project p ON p.workspace_id=t.workspace_id AND p.id=t.project_id
            LEFT JOIN app_user u ON u.id=t.assignee_id
            """;
    private static final String ACTIVITY_SELECT = """
            SELECT a.*, u.display_name AS actor_name FROM activity_event a JOIN app_user u ON u.id=a.actor_id
            """;

    public List<Workspace> workspaces(String actor) {
        return jdbc.query("""
                SELECT w.id,w.name,w.created_at,m.role FROM workspace w JOIN workspace_member m ON m.workspace_id=w.id
                WHERE m.user_id=? ORDER BY w.created_at,w.id
                """, (r, n) -> new Workspace(r.getString("id"), r.getString("name"),
                Role.valueOf(r.getString("role")), timestamp(r, "created_at")), actor);
    }

    @Transactional
    public Workspace createWorkspace(String actor, CreateWorkspace input) {
        String id = id();
        OffsetDateTime now = now();
        String name = input.name().strip();
        jdbc.update("INSERT INTO workspace(id,name,created_at) VALUES(?,?,?)", id, name, now);
        jdbc.update("INSERT INTO workspace_member(workspace_id,user_id,role,created_at) VALUES(?,?,'OWNER',?)", id, actor, now);
        audit(id, actor, "created", "workspace", name);
        return new Workspace(id, name, Role.OWNER, now);
    }

    public List<Member> members(String workspace, String actor) {
        requireMember(workspace, actor);
        return jdbc.query("""
                SELECT u.id,u.display_name,u.email,m.role FROM workspace_member m JOIN app_user u ON u.id=m.user_id
                WHERE m.workspace_id=? ORDER BY CASE m.role WHEN 'OWNER' THEN 0 WHEN 'MEMBER' THEN 1 ELSE 2 END,u.display_name,u.id
                """, MEMBER_ROW, workspace);
    }

    @Transactional
    public Member addMember(String workspace, String actor, AddMember input) {
        lockWorkspace(workspace, actor);
        requireOwner(workspace, actor);
        if (input.role() == Role.OWNER) throw badRequest("Invite members as MEMBER or VIEWER; promote them after joining.");
        List<String> users = jdbc.query("SELECT id FROM app_user WHERE email=?", (r,n) -> r.getString(1), input.email().strip().toLowerCase(Locale.ROOT));
        if (users.isEmpty()) throw notFound("No registered account has that email address.");
        String user = users.get(0);
        try {
            jdbc.update("INSERT INTO workspace_member(workspace_id,user_id,role,created_at) VALUES(?,?,?,?)", workspace, user, input.role().name(), now());
        } catch (DuplicateKeyException e) {
            throw conflict("This account is already a workspace member.");
        }
        Member result = member(workspace, user);
        audit(workspace, actor, "added", "member", result.name());
        return result;
    }

    @Transactional
    public Member updateMember(String workspace, String actor, String user, UpdateMember input) {
        lockWorkspace(workspace, actor);
        requireOwner(workspace, actor);
        Member before = member(workspace, user);
        if (actor.equals(user) && input.role() != Role.OWNER) throw conflict("Owners cannot demote themselves.");
        if (before.role() == Role.OWNER && input.role() != Role.OWNER && ownerCount(workspace) <= 1) {
            throw conflict("The workspace must retain at least one owner.");
        }
        jdbc.update("UPDATE workspace_member SET role=? WHERE workspace_id=? AND user_id=?", input.role().name(), workspace, user);
        if (before.role() != input.role()) audit(workspace, actor, "changed role of", "member", before.name());
        return member(workspace, user);
    }

    @Transactional
    public void removeMember(String workspace, String actor, String user) {
        lockWorkspace(workspace, actor);
        requireOwner(workspace, actor);
        Member before = member(workspace, user);
        if (before.role() == Role.OWNER) throw conflict("Demote another owner before removing them.");
        jdbc.update("UPDATE task SET assignee_id=NULL,version=version+1,updated_at=? WHERE workspace_id=? AND assignee_id=?", now(), workspace, user);
        jdbc.update("DELETE FROM workspace_member WHERE workspace_id=? AND user_id=?", workspace, user);
        audit(workspace, actor, "removed", "member", before.name());
    }

    public List<Project> projects(String workspace, String actor) {
        requireMember(workspace, actor);
        return projectsIn(workspace);
    }

    @Transactional
    public Project createProject(String workspace, String actor, CreateProject input) {
        lockWorkspace(workspace, actor);
        requireWriter(workspace, actor);
        String id = id();
        OffsetDateTime now = now();
        jdbc.update("INSERT INTO project(id,workspace_id,name,description,color,status,version,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',0,?,?)",
                id, workspace, input.name().strip(), text(input.description()), input.color().toLowerCase(Locale.ROOT), now, now);
        audit(workspace, actor, "created", "project", input.name().strip());
        return project(workspace, id);
    }

    @Transactional
    public Project updateProject(String workspace, String actor, String id, UpdateProject input) {
        lockWorkspace(workspace, actor);
        requireWriter(workspace, actor);
        project(workspace, id);
        int changed = jdbc.update("UPDATE project SET name=?,description=?,color=?,status=?,updated_at=?,version=version+1 WHERE workspace_id=? AND id=? AND version=?",
                input.name().strip(), text(input.description()), input.color().toLowerCase(Locale.ROOT), input.status().name(), now(), workspace, id, input.version());
        if (changed != 1) throw conflict("This project has changed. Refresh it before saving.");
        audit(workspace, actor, "updated", "project", input.name().strip());
        return project(workspace, id);
    }

    public Page<Task> tasks(String workspace, String actor, String q, TaskStatus status, Priority priority,
                             String projectId, String assigneeId, int page, int size) {
        requireMember(workspace, actor);
        validatePage(page, size);
        if (q != null && q.length() > 200) throw badRequest("Search must be at most 200 characters.");
        if (projectId != null && !projectId.isBlank()) project(workspace, projectId);
        if (assigneeId != null && !assigneeId.isBlank()) member(workspace, assigneeId);
        List<Object> args = new ArrayList<>();
        args.add(workspace);
        StringBuilder where = new StringBuilder(" WHERE t.workspace_id=?");
        if (q != null && !q.isEmpty()) {
            where.append(" AND (LOWER(t.title) LIKE ? ESCAPE '!' OR LOWER(t.description) LIKE ? ESCAPE '!')");
            String search = "%" + q.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            args.add(search); args.add(search);
        }
        if (status != null) { where.append(" AND t.status=?"); args.add(status.name()); }
        if (priority != null) { where.append(" AND t.priority=?"); args.add(priority.name()); }
        if (projectId != null && !projectId.isBlank()) { where.append(" AND t.project_id=?"); args.add(projectId); }
        if (assigneeId != null && !assigneeId.isBlank()) { where.append(" AND t.assignee_id=?"); args.add(assigneeId); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM task t" + where, Long.class, args.toArray());
        args.add(size); args.add((long) page * size);
        List<Task> items = jdbc.query(TASK_SELECT + where + " ORDER BY t.updated_at DESC,t.id LIMIT ? OFFSET ?", TASK_ROW, args.toArray());
        return page(items, page, size, total == null ? 0 : total);
    }

    public Task task(String workspace, String actor, String id) {
        requireMember(workspace, actor);
        return taskIn(workspace, id);
    }

    @Transactional
    public Task createTask(String workspace, String actor, CreateTask input) {
        validateDate(input.dueDate());
        lockWorkspace(workspace, actor);
        requireWriter(workspace, actor);
        validateReferences(workspace, input.projectId(), input.assigneeId());
        String id = id();
        OffsetDateTime now = now();
        jdbc.update("""
                INSERT INTO task(id,workspace_id,project_id,title,description,status,priority,assignee_id,due_date,version,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,0,?,?)
                """, id, workspace, input.projectId(), input.title().strip(), text(input.description()), input.status().name(), input.priority().name(), input.assigneeId(), input.dueDate(), now, now);
        audit(workspace, actor, "created", "task", input.title().strip());
        return taskIn(workspace, id);
    }

    @Transactional
    public Task updateTask(String workspace, String actor, String id, UpdateTask input) {
        validateDate(input.dueDate());
        lockWorkspace(workspace, actor);
        requireWriter(workspace, actor);
        Task before = taskIn(workspace, id);
        validateReferences(workspace, input.projectId(), input.assigneeId());
        int changed = jdbc.update("""
                UPDATE task SET project_id=?,title=?,description=?,status=?,priority=?,assignee_id=?,due_date=?,updated_at=?,version=version+1
                WHERE workspace_id=? AND id=? AND version=?
                """, input.projectId(), input.title().strip(), text(input.description()), input.status().name(), input.priority().name(), input.assigneeId(), input.dueDate(), now(), workspace, id, input.version());
        if (changed != 1) throw conflict("This task has changed. Refresh it before saving.");
        String action = before.status() != input.status() ? "moved to " + readable(input.status().name()) : "updated";
        audit(workspace, actor, action, "task", input.title().strip());
        return taskIn(workspace, id);
    }

    @Transactional
    public void deleteTask(String workspace, String actor, String id, long version) {
        if (version < 0) throw badRequest("Version must be zero or greater.");
        lockWorkspace(workspace, actor);
        requireWriter(workspace, actor);
        Task before = taskIn(workspace, id);
        int changed = jdbc.update("DELETE FROM task WHERE workspace_id=? AND id=? AND version=?", workspace, id, version);
        if (changed != 1) throw conflict("This task has changed. Refresh it before deleting.");
        audit(workspace, actor, "deleted", "task", before.title());
    }

    public List<Comment> comments(String workspace, String actor, String task) {
        requireMember(workspace, actor);
        taskIn(workspace, task);
        return jdbc.query("""
                SELECT c.*,u.display_name AS author_name FROM task_comment c JOIN app_user u ON u.id=c.author_id
                WHERE c.workspace_id=? AND c.task_id=? ORDER BY c.created_at,c.id
                """, COMMENT_ROW, workspace, task);
    }

    @Transactional
    public Comment createComment(String workspace, String actor, String task, CreateComment input) {
        lockWorkspace(workspace, actor);
        requireWriter(workspace, actor);
        Task target = taskIn(workspace, task);
        String id = id();
        jdbc.update("INSERT INTO task_comment(id,workspace_id,task_id,author_id,body,created_at) VALUES(?,?,?,?,?,?)", id, workspace, task, actor, input.body().strip(), now());
        audit(workspace, actor, "commented on", "task", target.title());
        return jdbc.query("SELECT c.*,u.display_name AS author_name FROM task_comment c JOIN app_user u ON u.id=c.author_id WHERE c.workspace_id=? AND c.id=?",
                COMMENT_ROW, workspace, id).get(0);
    }

    public Page<Activity> activity(String workspace, String actor, int page, int size) {
        requireMember(workspace, actor);
        validatePage(page, size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM activity_event WHERE workspace_id=?", Long.class, workspace);
        List<Activity> items = jdbc.query(ACTIVITY_SELECT + " WHERE a.workspace_id=? ORDER BY a.created_at DESC,a.id LIMIT ? OFFSET ?", ACTIVITY_ROW, workspace, size, (long) page * size);
        return page(items, page, size, total == null ? 0 : total);
    }

    public Overview overview(String workspace, String actor) {
        requireMember(workspace, actor);
        long total = count("SELECT COUNT(*) FROM task WHERE workspace_id=?", workspace);
        long done = count("SELECT COUNT(*) FROM task WHERE workspace_id=? AND status='DONE'", workspace);
        long progress = count("SELECT COUNT(*) FROM task WHERE workspace_id=? AND status='IN_PROGRESS'", workspace);
        long overdue = count("SELECT COUNT(*) FROM task WHERE workspace_id=? AND status<>'DONE' AND due_date<?", workspace, LocalDate.now(ZoneOffset.UTC));
        List<Activity> events = jdbc.query(ACTIVITY_SELECT + " WHERE a.workspace_id=? ORDER BY a.created_at DESC,a.id LIMIT 8", ACTIVITY_ROW, workspace);
        List<Task> upcoming = jdbc.query(TASK_SELECT + " WHERE t.workspace_id=? AND t.status<>'DONE' AND t.due_date IS NOT NULL ORDER BY t.due_date,t.id LIMIT 8", TASK_ROW, workspace);
        return new Overview(total, done, progress, overdue, projectsIn(workspace), events, upcoming);
    }

    public String export(String workspace, String actor) {
        requireMember(workspace, actor);
        List<Task> tasks = jdbc.query(TASK_SELECT + " WHERE t.workspace_id=? ORDER BY p.name,t.created_at,t.id", TASK_ROW, workspace);
        StringBuilder csv = new StringBuilder("Title,Description,Project,Status,Priority,Assignee,Due date\r\n");
        for (Task task : tasks) {
            String[] fields = {task.title(), task.description(), task.projectName(), task.status().name(),
                    task.priority().name(), task.assigneeName(), task.dueDate() == null ? "" : task.dueDate().toString()};
            for (int i = 0; i < fields.length; i++) {
                if (i != 0) csv.append(',');
                csv.append(csvCell(fields[i]));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    static String csvCell(String input) {
        String value = input == null ? "" : input;
        String stripped = value.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0
                || value.indexOf('\t') >= 0 || value.indexOf('\r') >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private List<Project> projectsIn(String workspace) {
        return jdbc.query(PROJECT_SELECT + " WHERE p.workspace_id=? ORDER BY CASE p.status WHEN 'ACTIVE' THEN 0 ELSE 1 END,p.created_at,p.id", PROJECT_ROW, workspace);
    }

    private Project project(String workspace, String id) {
        List<Project> rows = jdbc.query(PROJECT_SELECT + " WHERE p.workspace_id=? AND p.id=?", PROJECT_ROW, workspace, id);
        if (rows.isEmpty()) throw notFound("Project not found in this workspace.");
        return rows.get(0);
    }

    private Task taskIn(String workspace, String id) {
        List<Task> rows = jdbc.query(TASK_SELECT + " WHERE t.workspace_id=? AND t.id=?", TASK_ROW, workspace, id);
        if (rows.isEmpty()) throw notFound("Task not found in this workspace.");
        return rows.get(0);
    }

    private Member member(String workspace, String user) {
        List<Member> rows = jdbc.query("SELECT u.id,u.display_name,u.email,m.role FROM workspace_member m JOIN app_user u ON u.id=m.user_id WHERE m.workspace_id=? AND m.user_id=?", MEMBER_ROW, workspace, user);
        if (rows.isEmpty()) throw notFound("Member not found in this workspace.");
        return rows.get(0);
    }

    private Role requireMember(String workspace, String actor) {
        List<String> roles = jdbc.query("SELECT role FROM workspace_member WHERE workspace_id=? AND user_id=?", (r,n) -> r.getString(1), workspace, actor);
        if (roles.isEmpty()) throw notFound("Workspace not found.");
        return Role.valueOf(roles.get(0));
    }

    private void requireWriter(String workspace, String actor) {
        if (requireMember(workspace, actor) == Role.VIEWER) throw forbidden("Viewers have read-only access to this workspace.");
    }

    private void requireOwner(String workspace, String actor) {
        if (requireMember(workspace, actor) != Role.OWNER) throw forbidden("Only workspace owners can manage members.");
    }

    /** Locks serialize membership changes with writes that reference membership, preventing authorization races. */
    private void lockWorkspace(String workspace, String actor) {
        requireMember(workspace, actor);
        List<String> rows = jdbc.query("SELECT id FROM workspace WHERE id=? FOR UPDATE", (r,n) -> r.getString(1), workspace);
        if (rows.isEmpty()) throw notFound("Workspace not found.");
        requireMember(workspace, actor);
    }

    private void validateReferences(String workspace, String project, String assignee) {
        project(workspace, project);
        if (assignee != null) member(workspace, assignee);
    }

    private long ownerCount(String workspace) { return count("SELECT COUNT(*) FROM workspace_member WHERE workspace_id=? AND role='OWNER'", workspace); }
    private long count(String sql, Object... args) { Long count = jdbc.queryForObject(sql, Long.class, args); return count == null ? 0 : count; }

    private void audit(String workspace, String actor, String action, String type, String name) {
        jdbc.update("INSERT INTO activity_event(id,workspace_id,actor_id,action,entity_type,entity_name,created_at) VALUES(?,?,?,?,?,?,?)", id(), workspace, actor, action, type, name, now());
    }

    static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw badRequest("Page must be zero or greater; size must be between 1 and 100.");
    }

    private static void validateDate(LocalDate date) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) throw badRequest("Due date must use a year between 0001 and 9999.");
    }

    private static <T> Page<T> page(List<T> items, int page, int size, long total) { return new Page<>(items, page, size, total, (total + size - 1) / size); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private static String text(String input) { return input == null ? "" : input.strip(); }
    private static String readable(String input) { return input.toLowerCase(Locale.ROOT).replace('_', ' '); }
    private static OffsetDateTime timestamp(ResultSet result, String column) throws SQLException { return result.getObject(column, OffsetDateTime.class); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException forbidden(String message) { return new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    private static final RowMapper<Member> MEMBER_ROW = (r,n) -> new Member(r.getString("id"), r.getString("display_name"), r.getString("email"), Role.valueOf(r.getString("role")));
    private static final RowMapper<Project> PROJECT_ROW = (r,n) -> new Project(r.getString("id"), r.getString("name"), r.getString("description"), r.getString("color"),
            ProjectStatus.valueOf(r.getString("status")), r.getLong("task_count"), r.getLong("completed_count"), timestamp(r,"created_at"), r.getLong("version"));
    private static final RowMapper<Task> TASK_ROW = (r,n) -> new Task(r.getString("id"), r.getString("title"), r.getString("description"), r.getString("project_id"), r.getString("project_name"),
            r.getString("project_color"), TaskStatus.valueOf(r.getString("status")), Priority.valueOf(r.getString("priority")), r.getString("assignee_id"), r.getString("assignee_name"),
            r.getObject("due_date", LocalDate.class), timestamp(r,"created_at"), timestamp(r,"updated_at"), r.getLong("version"), r.getLong("comment_count"));
    private static final RowMapper<Comment> COMMENT_ROW = (r,n) -> new Comment(r.getString("id"), r.getString("author_id"), r.getString("author_name"), r.getString("body"), timestamp(r,"created_at"));
    private static final RowMapper<Activity> ACTIVITY_ROW = (r,n) -> new Activity(r.getString("id"), r.getString("actor_name"), r.getString("action"), r.getString("entity_type"), r.getString("entity_name"), timestamp(r,"created_at"));
}
