package com.orbit;

import com.jayway.jsonpath.JsonPath;
import com.orbit.domain.DomainModels;
import com.orbit.domain.DomainService;
import com.orbit.domain.ProductivityModels;
import com.orbit.domain.SavedViewService;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"orbit.demo.enabled=false", "orbit.auth.rate-limit-enabled=false",
        "orbit.accounts.email-verification-required=false", "orbit.accounts.registration-enabled=true", "orbit.mail.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ProductivityContract {
    private static final String PASSWORD = "OrbitProductivity!2026";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DomainService domain;
    @Autowired SavedViewService views;

    @Test
    void bulkUpdatesRequireWriteAccessAndPreserveFieldsAndResponseOrder() throws Exception {
        Client owner = account(), member = account(), viewer = account(), stranger = account();
        String workspace = workspace(owner), project = project(owner, workspace);
        add(owner, workspace, member, "MEMBER"); add(owner, workspace, viewer, "VIEWER");
        String first = task(owner, workspace, project, "First task"), second = task(owner, workspace, project, "Second task");
        String body = json("taskIds", List.of(second, first), "versions", Map.of(first, 0, second, 0),
                "status", "IN_PROGRESS", "priority", "URGENT", "assigneeId", owner.id);
        long events = events(workspace);
        call(viewer, "POST", base(workspace) + "/tasks/bulk", body, 403);
        call(stranger, "POST", base(workspace) + "/tasks/bulk", body, 404);
        MvcResult result = call(member, "POST", base(workspace) + "/tasks/bulk", body, 200);
        assertThat((List<String>) value(result, "$[*].id")).containsExactly(second, first);
        assertThat((List<String>) value(result, "$[*].status")).containsOnly("IN_PROGRESS");
        assertThat((List<String>) value(result, "$[*].priority")).containsOnly("URGENT");
        assertThat((List<String>) value(result, "$[*].assigneeId")).containsOnly(owner.id);
        assertThat(number(result, "$[0].version")).isEqualTo(1);
        assertThat((String) value(result, "$[0].title")).isEqualTo("Second task");
        assertThat((String) value(result, "$[0].description")).isEqualTo("Keep this description");
        assertThat((String) value(result, "$[0].dueDate")).isEqualTo("2027-01-12");
        assertThat((String) value(result, "$[0].projectId")).isEqualTo(project);
        assertThat(events(workspace)).isEqualTo(events + 2);
        assertThat(assignments(workspace, owner.id)).isEqualTo(2);
        assertThat(assignments(workspace, member.id)).isZero();
    }

    @Test
    void staleMissingAndForeignTasksOrAssigneesAbortTheEntireBatch() throws Exception {
        Client owner = account(), outsider = account();
        String workspace = workspace(owner), project = project(owner, workspace);
        String first = task(owner, workspace, project, "Untouched first"), second = task(owner, workspace, project, "Stale second");
        String foreignWorkspace = workspace(outsider), foreign = task(outsider, foreignWorkspace, project(outsider, foreignWorkspace), "Foreign task");
        call(owner, "POST", base(workspace) + "/tasks/bulk", json("taskIds", List.of(second), "versions", Map.of(second, 0), "priority", "HIGH"), 200);
        long events = events(workspace), notifications = notifications(workspace);
        call(owner, "POST", base(workspace) + "/tasks/bulk", json("taskIds", List.of(first, second), "versions", Map.of(first, 0, second, 0),
                "status", "DONE", "assigneeId", owner.id), 409);
        for (String inaccessible : List.of(foreign, UUID.randomUUID().toString())) {
            call(owner, "POST", base(workspace) + "/tasks/bulk", json("taskIds", List.of(first, inaccessible),
                    "versions", Map.of(first, 0, inaccessible, 0), "status", "DONE"), 404);
        }
        call(owner, "POST", base(workspace) + "/tasks/bulk", json("taskIds", List.of(first), "versions", Map.of(first, 0), "assigneeId", outsider.id), 404);
        MvcResult unchanged = call(owner, "GET", base(workspace) + "/tasks/" + first, null, 200);
        assertThat(number(unchanged, "$.version")).isZero();
        assertThat((String) value(unchanged, "$.status")).isEqualTo("TODO");
        assertThat((Object) value(unchanged, "$.assigneeId")).isNull();
        assertThat(events(workspace)).isEqualTo(events);
        assertThat(notifications(workspace)).isEqualTo(notifications);
    }

    @Test
    void bulkValidationAndClearingAssignmentsRespectTheHundredTaskBoundary() throws Exception {
        Client owner = account(), assignee = account();
        String workspace = workspace(owner), project = project(owner, workspace);
        add(owner, workspace, assignee, "MEMBER");
        String first = task(owner, workspace, project, "One"), second = task(owner, workspace, project, "Two");
        for (String invalid : List.of(
                json("taskIds", List.of(), "versions", Map.of(), "status", "DONE"),
                json("taskIds", List.of(first, first), "versions", Map.of(first, 0), "status", "DONE"),
                json("taskIds", List.of(first), "versions", Map.of(), "status", "DONE"),
                json("taskIds", List.of(first), "versions", Map.of(first, 0, second, 0), "status", "DONE"),
                json("taskIds", List.of(first), "versions", Map.of(first, -1), "status", "DONE"),
                json("taskIds", List.of(first), "versions", Map.of(first, 0)),
                json("taskIds", List.of(first), "versions", Map.of(first, 0), "clearAssignee", false),
                json("taskIds", List.of(first), "versions", Map.of(first, 0), "assigneeId", assignee.id, "clearAssignee", true),
                json("taskIds", List.of("not-a-uuid"), "versions", Map.of("not-a-uuid", 0), "status", "DONE"))) {
            call(owner, "POST", base(workspace) + "/tasks/bulk", invalid, 400);
        }
        call(owner, "POST", base(workspace) + "/tasks/bulk", json("taskIds", List.of(first, second), "versions", Map.of(first, 0, second, 0), "assigneeId", assignee.id), 200);
        assertThat(assignments(workspace, assignee.id)).isEqualTo(2);
        MvcResult cleared = call(owner, "POST", base(workspace) + "/tasks/bulk", json("taskIds", List.of(first, second), "versions", Map.of(first, 1, second, 1), "clearAssignee", true), 200);
        assertThat((List<Object>) value(cleared, "$[*].assigneeId")).containsOnlyNulls();
        assertThat(number(cleared, "$[0].version")).isEqualTo(2);
        assertThat(assignments(workspace, assignee.id)).isEqualTo(2);

        List<String> hundred = new ArrayList<>();
        Map<String, Long> versions = new LinkedHashMap<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (int index = 0; index < 100; index++) {
            String id = UUID.randomUUID().toString(); hundred.add(id); versions.put(id, 0L);
            jdbc.update("INSERT INTO task(id,workspace_id,project_id,title,status,priority,created_at,updated_at) VALUES(?,?,?,?,'TODO','LOW',?,?)",
                    id, workspace, project, "Boundary task " + index, now, now);
        }
        List<String> tooMany = new ArrayList<>(hundred); tooMany.add(first);
        Map<String, Long> tooManyVersions = new LinkedHashMap<>(versions); tooManyVersions.put(first, 2L);
        call(owner, "POST", base(workspace) + "/tasks/bulk", json("taskIds", tooMany, "versions", tooManyVersions, "status", "IN_REVIEW"), 400);
        MvcResult boundary = call(owner, "POST", base(workspace) + "/tasks/bulk", json("taskIds", hundred, "versions", versions, "status", "IN_REVIEW"), 200);
        assertThat((List<String>) value(boundary, "$[*].id")).containsExactlyElementsOf(hundred);
        assertThat((List<String>) value(boundary, "$[*].status")).hasSize(100).containsOnly("IN_REVIEW");
    }

    @Test
    void competingBatchesCommitOneWholeChangeAndRejectTheOther() throws Exception {
        Client owner = account();
        String workspace = workspace(owner), project = project(owner, workspace);
        String first = task(owner, workspace, project, "Concurrent one"), second = task(owner, workspace, project, "Concurrent two");
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        long events = events(workspace);
        try {
            var firstResult = pool.submit(() -> bulkAfter(start, workspace, owner.id, first, second, DomainModels.Priority.HIGH));
            var secondResult = pool.submit(() -> bulkAfter(start, workspace, owner.id, second, first, DomainModels.Priority.URGENT));
            start.countDown();
            assertThat(List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForList("SELECT version FROM task WHERE workspace_id=?", Long.class, workspace)).containsOnly(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT priority) FROM task WHERE workspace_id=?", Long.class, workspace)).isEqualTo(1);
        assertThat(events(workspace)).isEqualTo(events + 2);
    }

    @Test
    void savedViewsArePrivateVersionedAndUsableByViewersWithLiteralFilters() throws Exception {
        Client owner = account(), viewer = account();
        String workspace = workspace(owner), project = project(owner, workspace);
        add(owner, workspace, viewer, "VIEWER");
        String task = task(owner, workspace, project, "Deliver 100%_! safely");
        call(owner, "POST", base(workspace) + "/tasks/bulk", json("taskIds", List.of(task), "versions", Map.of(task, 0), "assigneeId", owner.id), 200);
        String literal = " 100%_! ";
        MvcResult created = call(viewer, "POST", base(workspace) + "/saved-views", json("label", "  My literal filter  ", "q", literal,
                "status", "TODO", "priority", "MEDIUM", "projectId", project, "assigneeId", owner.id), 201);
        String view = value(created, "$.id"), path = base(workspace) + "/saved-views/" + view;
        assertThat((String) value(created, "$.workspaceId")).isEqualTo(workspace);
        assertThat((String) value(created, "$.label")).isEqualTo("My literal filter");
        assertThat((String) value(created, "$.q")).isEqualTo(literal);
        assertThat((List<Object>) value(call(owner, "GET", base(workspace) + "/saved-views", null, 200), "$" )).isEmpty();
        call(owner, "GET", path, null, 404);
        call(owner, "PATCH", path, json("label", "Steal view", "version", 0), 404);
        call(owner, "DELETE", path, null, 404);
        MvcResult filtered = mvc.perform(request(HttpMethod.GET, base(workspace) + "/tasks").cookie(viewer.cookieArray())
                .param("q", literal).param("status", "TODO").param("priority", "MEDIUM")
                .param("projectId", project).param("assigneeId", owner.id)).andExpect(status().isOk()).andReturn();
        assertThat((List<String>) value(filtered, "$.items[*].id")).containsExactly(task);
        MvcResult updated = call(viewer, "PATCH", path, json("label", "All tasks", "version", 0), 200);
        assertThat(number(updated, "$.version")).isEqualTo(1);
        for (String filter : List.of("q", "status", "priority", "projectId", "assigneeId"))
            assertThat((Object) value(updated, "$." + filter)).isNull();
        call(viewer, "PATCH", path, json("label", "Old edit", "version", 0), 409);
        assertThat((String) value(call(viewer, "GET", path, null, 200), "$.label")).isEqualTo("All tasks");
        call(viewer, "DELETE", path, null, 204);
        call(viewer, "GET", path, null, 404);
    }

    @Test
    void savedViewValidationRejectsForeignReferencesAndCrossWorkspaceAccess() throws Exception {
        Client owner = account(), outsider = account();
        String workspace = workspace(owner), foreignWorkspace = workspace(outsider), foreignProject = project(outsider, foreignWorkspace);
        for (String invalid : List.of(json("label", " "), json("label", "x".repeat(81)),
                json("label", "Long search", "q", "x".repeat(201)), json("label", "Nul", "q", "a\u0000b"),
                json("label", "Bad project", "projectId", "not-a-uuid"), json("label", "Bad status", "status", "DELETED")))
            call(owner, "POST", base(workspace) + "/saved-views", invalid, 400);
        call(owner, "POST", base(workspace) + "/saved-views", json("label", "Foreign project", "projectId", foreignProject), 404);
        call(owner, "POST", base(workspace) + "/saved-views", json("label", "Foreign member", "assigneeId", outsider.id), 404);
        call(outsider, "GET", base(workspace) + "/saved-views", null, 404);
        call(outsider, "POST", base(workspace) + "/saved-views", json("label", "Intrusion"), 404);
        String view = value(call(owner, "POST", base(workspace) + "/saved-views", json("label", "Owned"), 201), "$.id");
        call(outsider, "GET", base(foreignWorkspace) + "/saved-views/" + view, null, 404);
        call(outsider, "PATCH", base(foreignWorkspace) + "/saved-views/" + view, json("label", "Wrong workspace", "version", 0), 404);
        call(owner, "PATCH", base(workspace) + "/saved-views/" + view, json("label", "Missing version"), 400);
        call(owner, "PATCH", base(workspace) + "/saved-views/" + view, json("label", "Negative version", "version", -1), 400);
        assertThat((List<Object>) value(call(owner, "GET", base(workspace) + "/saved-views", null, 200), "$" )).hasSize(1);
    }

    @Test
    void removingAMemberDeletesTheirPersonalViewsAndClearsRemainingAssigneeFilters() throws Exception {
        Client owner = account(), member = account();
        String workspace = workspace(owner); add(owner, workspace, member, "MEMBER");
        String ownView = value(call(owner, "POST", base(workspace) + "/saved-views", json("label", "Their work", "assigneeId", member.id), 201), "$.id");
        String memberView = value(call(member, "POST", base(workspace) + "/saved-views", json("label", "My work", "assigneeId", member.id), 201), "$.id");
        call(owner, "DELETE", base(workspace) + "/members/" + member.id, null, 204);
        MvcResult cleared = call(owner, "GET", base(workspace) + "/saved-views/" + ownView, null, 200);
        assertThat((Object) value(cleared, "$.assigneeId")).isNull();
        assertThat(number(cleared, "$.version")).isEqualTo(1);
        call(owner, "PATCH", base(workspace) + "/saved-views/" + ownView, json("label", "Stale after removal", "version", 0), 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM saved_task_view WHERE id=?", Long.class, memberView)).isZero();
        call(member, "GET", base(workspace) + "/saved-views", null, 404);
        add(owner, workspace, member, "MEMBER");
        assertThat((List<Object>) value(call(member, "GET", base(workspace) + "/saved-views", null, 200), "$" )).isEmpty();
    }

    @Test
    void concurrentSavedViewCreationCannotExceedThePrivateQuota() throws Exception {
        Client owner = account(); String workspace = workspace(owner);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (int index = 0; index < 99; index++)
            jdbc.update("INSERT INTO saved_task_view(id,workspace_id,user_id,label,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), workspace, owner.id, "Existing " + index, now, now);
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> viewAfter(start, workspace, owner.id, "First contender"));
            var second = pool.submit(() -> viewAfter(start, workspace, owner.id, "Second contender"));
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM saved_task_view WHERE workspace_id=? AND user_id=?", Long.class, workspace, owner.id)).isEqualTo(100);
        assertThat((List<Object>) value(call(owner, "GET", base(workspace) + "/saved-views", null, 200), "$" )).hasSize(100);
    }

    private int bulkAfter(CountDownLatch start, String workspace, String actor, String first, String second, DomainModels.Priority priority) throws InterruptedException {
        start.await();
        try {
            domain.bulkTasks(workspace, actor, new ProductivityModels.BulkTasks(List.of(first, second), Map.of(first, 0L, second, 0L), null, priority, null, false));
            return 200;
        } catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
    }
    private int viewAfter(CountDownLatch start, String workspace, String actor, String label) throws InterruptedException {
        start.await();
        try { views.create(workspace, actor, new ProductivityModels.CreateView(label, null, null, null, null, null)); return 201; }
        catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
    }
    private Client account() throws Exception {
        Client client = new Client(); csrf(client);
        client.email = "productivity-" + UUID.randomUUID() + "@orbit.test";
        client.id = value(call(client, "POST", "/api/auth/register", json("name", "Workflow user", "email", client.email, "password", PASSWORD), 201), "$.id");
        call(client, "POST", "/api/auth/login", json("email", client.email, "password", PASSWORD), 200); csrf(client); return client;
    }
    private String workspace(Client owner) throws Exception { return value(call(owner, "POST", "/api/workspaces", json("name", "Productivity " + UUID.randomUUID()), 201), "$.id"); }
    private String project(Client owner, String workspace) throws Exception { return value(call(owner, "POST", base(workspace) + "/projects", json("name", "Workflow project", "color", "#7c6af2"), 201), "$.id"); }
    private String task(Client owner, String workspace, String project, String title) throws Exception {
        return value(call(owner, "POST", base(workspace) + "/tasks", json("title", title, "description", "Keep this description", "projectId", project,
                "status", "TODO", "priority", "MEDIUM", "dueDate", "2027-01-12"), 201), "$.id");
    }
    private void add(Client owner, String workspace, Client user, String role) throws Exception { call(owner, "POST", base(workspace) + "/members", json("email", user.email, "role", role), 201); }
    private long events(String workspace) { return jdbc.queryForObject("SELECT COUNT(*) FROM activity_event WHERE workspace_id=?", Long.class, workspace); }
    private long notifications(String workspace) { return jdbc.queryForObject("SELECT COUNT(*) FROM user_notification WHERE workspace_id=?", Long.class, workspace); }
    private long assignments(String workspace, String user) { return jdbc.queryForObject("SELECT COUNT(*) FROM user_notification WHERE workspace_id=? AND user_id=? AND type='ASSIGNED'", Long.class, workspace, user); }
    private void csrf(Client client) throws Exception { MvcResult result = call(client, "GET", "/api/auth/csrf", null, 200); client.token = value(result, "$.token"); client.header = value(result, "$.headerName"); }
    private MvcResult call(Client client, String method, String path, String body, int expected) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path);
        if (!client.cookies.isEmpty()) builder.cookie(client.cookieArray());
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
        if (!method.equals("GET") && client.token != null) builder.header(client.header, client.token);
        MvcResult result = mvc.perform(builder).andExpect(status().is(expected)).andReturn();
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            String pair = header.split(";", 2)[0]; int separator = pair.indexOf('=');
            String name = pair.substring(0, separator), value = pair.substring(separator + 1);
            if (value.isEmpty() || header.contains("Max-Age=0")) client.cookies.remove(name); else client.cookies.put(name, value);
        }
        return result;
    }
    private static String base(String workspace) { return "/api/workspaces/" + workspace; }
    private static <T> T value(MvcResult result, String path) throws Exception { return JsonPath.read(result.getResponse().getContentAsString(), path); }
    private static long number(MvcResult result, String path) throws Exception { return ((Number) value(result, path)).longValue(); }
    private static String json(Object... entries) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) fields.put(entries[index].toString(), entries[index + 1]);
        return JSON.writeValueAsString(fields);
    }
    private static final class Client {
        String id, email, token, header;
        final Map<String, String> cookies = new LinkedHashMap<>();
        Cookie[] cookieArray() { return cookies.entrySet().stream().map(entry -> new Cookie(entry.getKey(), entry.getValue())).toArray(Cookie[]::new); }
    }
}
