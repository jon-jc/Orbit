package com.orbit;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the same HTTP/security contract against H2 and the opt-in real PostgreSQL database. */
@SpringBootTest(properties = {"orbit.demo.enabled=false", "orbit.auth.rate-limit-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class WorkflowContract {
    private static final String PASSWORD = "OrbitIntegration!2026";
    @Autowired MockMvc mvc;

    @Test
    void authenticationRequiresCsrfRotatesTheSessionAndLogoutRevokesIt() throws Exception {
        Client client = anonymous();
        MvcResult unauthorized=call(client, "GET", "/api/auth/me", null, 401);
        assertThat(string(unauthorized,"$.requestId")).isEqualTo(unauthorized.getResponse().getHeader("X-Request-ID"));
        call(client, "GET", "/api/workspaces", null, 401);
        String email = uniqueEmail();
        call(client, "POST", "/api/auth/register", json("name", "Alex Test", "email", email, "password", PASSWORD), 201);
        call(client, "GET", "/api/auth/me", null, 401);
        String priorSession = client.cookies.get("ORBIT_SESSION");
        MvcResult login = call(client, "POST", "/api/auth/login", json("email", email, "password", PASSWORD), 200);
        assertThat(client.cookies.get("ORBIT_SESSION")).isNotBlank().isNotEqualTo(priorSession);
        assertThat(login.getResponse().getHeaders("Set-Cookie")).anySatisfy(header -> {
            assertThat(header).contains("ORBIT_SESSION=", "HttpOnly").containsIgnoringCase("SameSite=Lax");
        });
        refreshCsrf(client);
        assertThat(string(call(client, "GET", "/api/auth/me", null, 200), "$.email")).isEqualTo(email);
        mvc.perform(request(HttpMethod.POST, "/api/workspaces").cookie(client.cookieArray())
                .contentType(MediaType.APPLICATION_JSON).content(json("name", "No token")))
                .andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.POST, "/api/workspaces").cookie(client.cookieArray())
                .header(client.header, "invalid-token").contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "Invalid token"))).andExpect(status().isForbidden());
        call(client, "POST", "/api/auth/logout", null, 204);
        call(client, "GET", "/api/auth/me", null, 401);
    }

    @Test
    void registrationLoginAndDomainValidationReturnUsefulErrors() throws Exception {
        Client anon = anonymous();
        MvcResult invalid = call(anon, "POST", "/api/auth/register",
                json("name", "", "email", "broken", "password", "short"), 400);
        assertThat(number(invalid, "$.status")).isEqualTo(400);
        assertThat(string(invalid, "$.requestId")).isNotBlank();
        String email = uniqueEmail();
        String account = json("name", "Validation Test", "email", email, "password", PASSWORD);
        call(anon, "POST", "/api/auth/register", account, 201);
        call(anon, "POST", "/api/auth/register", account, 409);
        call(anon, "POST", "/api/auth/login", json("email", email, "password", "IncorrectPassword!2026"), 401);
        call(anon, "POST", "/api/auth/login", json("email", email, "password", PASSWORD), 200);
        refreshCsrf(anon);
        call(anon, "POST", "/api/workspaces", json("name", " "), 400);
        String workspace = workspace(anon);
        call(anon, "POST", base(workspace) + "/projects", json("name", "Project", "description", "", "color", "red"), 400);
        String project = project(anon, workspace);
        call(anon, "POST", base(workspace) + "/tasks", taskJson("Task", project, "INVALID", "HIGH", null, null, null), 400);
        call(anon, "POST", base(workspace) + "/tasks", taskJson("Task", project, "TODO", "HIGH", null, "2026-02-30", null), 400);
        call(anon, "POST", base(workspace) + "/tasks", taskJson("Task", project, "TODO", "HIGH", null, "+10000-01-01", null), 400);
        call(anon, "POST", base(workspace) + "/tasks",
                taskJson("RejectedNul", project, "TODO", "HIGH", null, null, null)
                        .replace("RejectedNul", "Rejected\\u0000Nul"), 400);
        call(anon, "GET", base(workspace) + "/tasks?page=-1", null, 400);
        call(anon, "GET", base(workspace) + "/tasks?size=101", null, 400);
        call(anon, "GET", base(workspace) + "/tasks?status=INVALID", null, 400);
        call(anon, "GET", base(workspace) + "/activity?size=0", null, 400);
    }

    @Test
    void workspaceAndObjectAccessCannotCrossTenantBoundaries() throws Exception {
        Client alice = account("Alice Owner");
        Client bob = account("Bob Owner");
        String wa = workspace(alice), wb = workspace(bob);
        String pa = project(alice, wa), pb = project(bob, wb);
        String tb = string(task(bob, wb, "Private launch", pb, "TODO", "HIGH", null, null), "$.id");
        call(alice, "GET", base(wb) + "/tasks", null, 404);
        call(alice, "GET", base(wb) + "/members", null, 404);
        call(alice, "GET", base(wb) + "/overview", null, 404);
        call(alice, "GET", base(wb) + "/activity", null, 404);
        call(alice, "GET", base(wb) + "/export", null, 404);
        call(alice, "GET", base(wa) + "/tasks/" + tb, null, 404);
        call(alice, "GET", base(wa) + "/tasks/" + tb + "/comments", null, 404);
        call(alice, "POST", base(wa) + "/tasks/" + tb + "/comments", json("body", "Cross-tenant write"), 404);
        call(alice, "PATCH", base(wa) + "/tasks/" + tb, taskJson("Hijacked", pa, "DONE", "LOW", null, null, 0L), 404);
        call(alice, "DELETE", base(wa) + "/tasks/" + tb + "?version=0", null, 404);
        call(alice, "POST", base(wa) + "/tasks", taskJson("Wrong project", pb, "TODO", "HIGH", null, null, null), 404);
        call(alice, "POST", base(wa) + "/tasks", taskJson("Wrong assignee", pa, "TODO", "HIGH", bob.id, null, null), 404);
        assertThat(string(call(bob, "GET", base(wb) + "/tasks/" + tb, null, 200), "$.title")).isEqualTo("Private launch");
    }

    @Test
    void rolesPermitCollaborationButProtectOwnerAndViewerBoundaries() throws Exception {
        Client owner = account("Owner"), member = account("Member"), viewer = account("Viewer");
        String w = workspace(owner), p = project(owner, w);
        addMember(owner, w, member, "MEMBER");
        addMember(owner, w, viewer, "VIEWER");
        call(member, "GET", base(w) + "/members", null, 200);
        call(viewer, "GET", base(w) + "/projects", null, 200);
        MvcResult created = task(member, w, "Collaborative task", p, "TODO", "MEDIUM", member.id, null);
        String t = string(created, "$.id");
        call(member, "POST", base(w) + "/tasks/" + t + "/comments", json("body", "I can work on this."), 201);
        call(viewer, "GET", base(w) + "/tasks/" + t, null, 200);
        call(viewer, "POST", base(w) + "/projects", json("name", "Forbidden", "description", "", "color", "#7367f0"), 403);
        call(viewer, "POST", base(w) + "/tasks", taskJson("Forbidden", p, "TODO", "LOW", null, null, null), 403);
        call(viewer, "PATCH", base(w) + "/tasks/" + t, taskJson("Forbidden", p, "DONE", "LOW", null, null, number(created, "$.version")), 403);
        call(viewer, "DELETE", base(w) + "/tasks/" + t + "?version=" + number(created, "$.version"), null, 403);
        call(viewer, "POST", base(w) + "/tasks/" + t + "/comments", json("body", "Forbidden"), 403);
        call(member, "POST", base(w) + "/members", json("email", uniqueEmail(), "role", "VIEWER"), 403);
        call(member, "PATCH", base(w) + "/members/" + viewer.id, json("role", "MEMBER"), 403);
        call(member, "DELETE", base(w) + "/members/" + viewer.id, null, 403);
        call(owner, "PATCH", base(w) + "/members/" + owner.id, json("role", "VIEWER"), 409);
        call(owner, "DELETE", base(w) + "/members/" + owner.id, null, 409);
        call(owner, "PATCH", base(w) + "/members/" + viewer.id, json("role", "MEMBER"), 200);
        call(viewer, "POST", base(w) + "/tasks/" + t + "/comments", json("body", "Role change takes effect now."), 201);
        call(owner, "DELETE", base(w) + "/members/" + viewer.id, null, 204);
        call(viewer, "GET", base(w) + "/tasks/" + t, null, 404);
    }

    @Test
    void editsUseOptimisticVersionsAndNeverSilentlyLoseChanges() throws Exception {
        Client owner = account("Optimistic Owner");
        String w = workspace(owner), p = project(owner, w);
        MvcResult created = task(owner, w, "Original title", p, "TODO", "HIGH", null, null);
        String t = string(created, "$.id");
        long originalVersion = number(created, "$.version");
        MvcResult updated = call(owner, "PATCH", base(w) + "/tasks/" + t,
                taskJson("Updated title", p, "IN_PROGRESS", "URGENT", null, null, originalVersion), 200);
        assertThat(number(updated, "$.version")).isEqualTo(originalVersion + 1);
        call(owner, "PATCH", base(w) + "/tasks/" + t,
                taskJson("Stale title", p, "DONE", "LOW", null, null, originalVersion), 409);
        call(owner, "DELETE", base(w) + "/tasks/" + t + "?version=" + originalVersion, null, 409);
        assertThat(string(call(owner, "GET", base(w) + "/tasks/" + t, null, 200), "$.title")).isEqualTo("Updated title");
        call(owner, "DELETE", base(w) + "/tasks/" + t, null, 400);
        call(owner, "DELETE", base(w) + "/tasks/" + t + "?version=" + number(updated, "$.version"), null, 204);
        call(owner, "GET", base(w) + "/tasks/" + t, null, 404);
        MvcResult projects = call(owner, "GET", base(w) + "/projects", null, 200);
        long pv = number(projects, "$[0].version");
        String patch = json("name", "Archived project", "description", "Finished", "color", "#7367f0", "status", "ARCHIVED", "version", pv);
        call(owner, "PATCH", base(w) + "/projects/" + p, patch, 200);
        call(owner, "PATCH", base(w) + "/projects/" + p, patch, 409);
    }

    @Test
    void taskCommentsOverviewFiltersPaginationAndAuditWorkTogether() throws Exception {
        Client owner = account("Workflow Owner");
        String w = workspace(owner), p = project(owner, w);
        String tomorrow = LocalDate.now().plusDays(1).toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        MvcResult first = task(owner, w, "Launch 100%", p, "IN_PROGRESS", "URGENT", owner.id, tomorrow);
        String t = string(first, "$.id");
        task(owner, w, "Completed design", p, "DONE", "LOW", null, yesterday);
        task(owner, w, "Overdue_review!", p, "TODO", "HIGH", null, yesterday);
        call(owner, "POST", base(w) + "/tasks/" + t + "/comments", json("body", "Reviewed <script>alert(1)</script> & ready."), 201);
        MvcResult comments = call(owner, "GET", base(w) + "/tasks/" + t + "/comments", null, 200);
        assertThat(string(comments, "$[0].body")).isEqualTo("Reviewed <script>alert(1)</script> & ready.");
        assertThat(string(comments, "$[0].authorName")).isEqualTo("Workflow Owner");
        assertThat(number(call(owner, "GET", base(w) + "/tasks/" + t, null, 200), "$.commentCount")).isEqualTo(1);
        call(owner, "POST", base(w) + "/tasks/" + t + "/comments", json("body", " "), 400);
        MvcResult overview = call(owner, "GET", base(w) + "/overview", null, 200);
        assertThat(number(overview, "$.totalTasks")).isEqualTo(3);
        assertThat(number(overview, "$.completedTasks")).isEqualTo(1);
        assertThat(number(overview, "$.inProgressTasks")).isEqualTo(1);
        assertThat(number(overview, "$.overdueTasks")).isEqualTo(1);
        assertThat(number(overview, "$.projects[0].taskCount")).isEqualTo(3);
        assertThat(number(overview, "$.projects[0].completedTaskCount")).isEqualTo(1);
        MvcResult filtered = call(owner, "GET", base(w) + "/tasks?status=IN_PROGRESS&priority=URGENT&projectId=" + p + "&assigneeId=" + owner.id, null, 200);
        assertThat(number(filtered, "$.total")).isEqualTo(1);
        assertThat(string(filtered, "$.items[0].id")).isEqualTo(t);
        MvcResult literal = mvc.perform(request(HttpMethod.GET, base(w) + "/tasks")
                .cookie(owner.cookieArray()).param("q", "%")).andExpect(status().isOk()).andReturn();
        assertThat(number(literal, "$.total")).isEqualTo(1);
        for (String escapedCharacter : List.of("_", "!")) {
            MvcResult escaped = mvc.perform(request(HttpMethod.GET, base(w) + "/tasks")
                    .cookie(owner.cookieArray()).param("q", escapedCharacter)).andExpect(status().isOk()).andReturn();
            assertThat(number(escaped, "$.total")).isEqualTo(1);
        }
        MvcResult caseInsensitive = mvc.perform(request(HttpMethod.GET, base(w) + "/tasks")
                .cookie(owner.cookieArray()).param("q", "lAUnCh")).andExpect(status().isOk()).andReturn();
        assertThat(number(caseInsensitive, "$.total")).isEqualTo(1);
        MvcResult page0 = call(owner, "GET", base(w) + "/tasks?page=0&size=2", null, 200);
        MvcResult page1 = call(owner, "GET", base(w) + "/tasks?page=1&size=2", null, 200);
        assertThat(number(page0, "$.total")).isEqualTo(3);
        assertThat(number(page0, "$.totalPages")).isEqualTo(2);
        assertThat(list(page0, "$.items[*].id")).hasSize(2).doesNotContainAnyElementsOf(list(page1, "$.items[*].id"));
        assertThat(list(page1, "$.items[*].id")).hasSize(1);
        MvcResult activity = call(owner, "GET", base(w) + "/activity?page=0&size=100", null, 200);
        assertThat(list(activity, "$.items[*].entityName")).contains("Launch 100%", "Completed design", "Overdue_review!");
        assertThat(list(activity, "$.items[*].actorName")).containsOnly("Workflow Owner");
        assertThat(number(activity, "$.total")).isGreaterThanOrEqualTo(5);
    }

    @Test
    void removingAnAssigneeUnassignsTasksAndInvalidatesStaleEdits() throws Exception {
        Client owner = account("Assignment Owner"), member = account("Assignee");
        String w = workspace(owner), p = project(owner, w);
        addMember(owner, w, member, "MEMBER");
        MvcResult task = task(owner, w, "Assigned work", p, "TODO", "HIGH", member.id, null);
        String t = string(task, "$.id");
        call(owner, "DELETE", base(w) + "/members/" + member.id, null, 204);
        MvcResult unassigned = call(owner, "GET", base(w) + "/tasks/" + t, null, 200);
        assertThat((Object) JsonPath.read(unassigned.getResponse().getContentAsString(), "$.assigneeId")).isNull();
        assertThat(number(unassigned, "$.version")).isEqualTo(number(task, "$.version") + 1);
        call(owner, "PATCH", base(w) + "/tasks/" + t,
                taskJson("Stale assignment", p, "DONE", "LOW", null, null, number(task, "$.version")), 409);
    }

    @Test
    void csvExportQuotesContentAndNeutralizesSpreadsheetFormulaInjection() throws Exception {
        Client owner = account("Export Owner");
        String w = workspace(owner), p = project(owner, w);
        task(owner, w, "=1+1", p, "TODO", "HIGH", null, null);
        task(owner, w, "  +SUM(A1:A2)", p, "TODO", "HIGH", null, null);
        task(owner, w, "Normal, \"quoted\" title", p, "DONE", "LOW", null, null);
        MvcResult exported = call(owner, "GET", base(w) + "/export", null, 200);
        assertThat(exported.getResponse().getContentType()).startsWith("text/csv");
        assertThat(exported.getResponse().getHeader("Content-Disposition")).contains("attachment");
        String csv = exported.getResponse().getContentAsString();
        assertThat(csv).startsWith("Title,Description,Project,Status,Priority,Assignee,Due date\r\n");
        assertThat(csv).contains("\"'=1+1\"", "\"'+SUM(A1:A2)\"", "\"Normal, \"\"quoted\"\" title\"");
        assertThat(csv).doesNotContain("\"=1+1\"", "\"+SUM(A1:A2)\"");
    }

    private Client anonymous() throws Exception {
        Client client = new Client();
        refreshCsrf(client);
        return client;
    }
    private Client account(String name) throws Exception {
        Client client = anonymous();
        client.email = uniqueEmail();
        MvcResult registered = call(client, "POST", "/api/auth/register", json("name", name, "email", client.email, "password", PASSWORD), 201);
        client.id = string(registered, "$.id");
        call(client, "POST", "/api/auth/login", json("email", client.email, "password", PASSWORD), 200);
        refreshCsrf(client);
        return client;
    }
    private void refreshCsrf(Client client) throws Exception {
        MvcResult result = call(client, "GET", "/api/auth/csrf", null, 200);
        client.token = string(result, "$.token");
        client.header = string(result, "$.headerName");
    }
    private String workspace(Client client) throws Exception {
        return string(call(client, "POST", "/api/workspaces", json("name", "Workspace " + UUID.randomUUID()), 201), "$.id");
    }
    private String project(Client client, String workspace) throws Exception {
        return string(call(client, "POST", base(workspace) + "/projects", json("name", "Launch project", "description", "Integration workflow", "color", "#7367f0"), 201), "$.id");
    }
    private void addMember(Client owner, String workspace, Client member, String role) throws Exception {
        call(owner, "POST", base(workspace) + "/members", json("email", member.email, "role", role), 201);
    }
    private MvcResult task(Client client, String workspace, String title, String project, String state,
                           String priority, String assignee, String due) throws Exception {
        return call(client, "POST", base(workspace) + "/tasks", taskJson(title, project, state, priority, assignee, due, null), 201);
    }
    private static String taskJson(String title, String project, String state, String priority, String assignee, String due, Long version) {
        String body = json("title", title, "description", "Detailed work", "projectId", project, "status", state,
                "priority", priority, "assigneeId", assignee, "dueDate", due);
        return version == null ? body : body.substring(0, body.length() - 1) + ",\"version\":" + version + "}";
    }
    private MvcResult call(Client client, String method, String path, String body, int expected) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path);
        if (!client.cookies.isEmpty()) builder.cookie(client.cookieArray());
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
        if (!method.equals("GET") && client.token != null) builder.header(client.header, client.token);
        MvcResult result = mvc.perform(builder).andExpect(status().is(expected)).andReturn();
        // Spring Session writes Set-Cookie headers. Preserve the browser cookie lifecycle, not a mocked SecurityContext.
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            String pair = header.substring(0, header.indexOf(';') < 0 ? header.length() : header.indexOf(';'));
            int separator = pair.indexOf('=');
            String name = pair.substring(0, separator), value = pair.substring(separator + 1);
            if (value.isEmpty() || header.contains("Max-Age=0")) client.cookies.remove(name);
            else client.cookies.put(name, value);
        }
        return result;
    }
    private static String base(String workspace) { return "/api/workspaces/" + workspace; }
    private static String uniqueEmail() { return "test-" + UUID.randomUUID() + "@orbit.test"; }
    private static String string(MvcResult result, String path) throws Exception { return JsonPath.read(result.getResponse().getContentAsString(), path); }
    private static long number(MvcResult result, String path) throws Exception { return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue(); }
    private static List<String> list(MvcResult result, String path) throws Exception { return JsonPath.read(result.getResponse().getContentAsString(), path); }
    private static String json(Object... entries) {
        StringBuilder text = new StringBuilder("{");
        for (int i = 0; i < entries.length; i += 2) {
            if (i > 0) text.append(',');
            text.append(quote(entries[i].toString())).append(':');
            Object value = entries[i + 1];
            text.append(value == null ? "null" : value instanceof Number || value instanceof Boolean ? value.toString() : quote(value.toString()));
        }
        return text.append('}').toString();
    }
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    private static final class Client {
        final Map<String, String> cookies = new LinkedHashMap<>();
        String token, header, id, email;
        Cookie[] cookieArray() { return cookies.entrySet().stream().map(c -> new Cookie(c.getKey(), c.getValue())).toArray(Cookie[]::new); }
    }
}
