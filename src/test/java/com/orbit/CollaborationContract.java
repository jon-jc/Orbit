package com.orbit;

import com.jayway.jsonpath.JsonPath;
import com.orbit.domain.CollaborationService;
import com.orbit.mail.MailService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"orbit.demo.enabled=false","orbit.auth.rate-limit-enabled=false",
        "orbit.accounts.email-verification-required=true","orbit.mail.enabled=true","orbit.mail.host=127.0.0.1",
        "orbit.mail.dispatch-delay-ms=86400000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class CollaborationContract {
    private static final String PASSWORD = "OrbitCollaboration!2026";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CollaborationService collaboration;
    @MockitoBean MailService mail;
    private final Map<String,List<Letter>> letters = new ConcurrentHashMap<>();
    private record Letter(String subject,String body) {}

    @BeforeEach
    void captureEmails() {
        letters.clear();
        org.mockito.stubbing.Answer<Object> capture = invocation -> {
            String to = invocation.getArgument(0);
            letters.computeIfAbsent(to,key -> new CopyOnWriteArrayList<>()).add(new Letter(invocation.getArgument(1),invocation.getArgument(2)));
            return null;
        };
        doAnswer(capture).when(mail).enqueue(anyString(),anyString(),anyString());
        doAnswer(capture).when(mail).enqueueAction(anyString(),anyString(),anyString(),anyString(),anyString(),any(OffsetDateTime.class));
    }

    @Test
    void onlyOwnersCanChangeVersionedSettingsAndManageInvitations() throws Exception {
        Client owner = account("Settings Owner"), member = account("Member"), viewer = account("Viewer"), stranger = account("Stranger");
        String workspace = workspace(owner);
        add(owner,workspace,member,"MEMBER"); add(owner,workspace,viewer,"VIEWER");
        MvcResult current = call(owner,"GET",base(workspace) + "/settings",null,200);
        long version = number(current,"$.version");
        String patch = json("name","Renamed Workspace","version",version);
        call(member,"GET",base(workspace) + "/settings",null,200);
        call(viewer,"GET",base(workspace) + "/settings",null,200);
        call(member,"PATCH",base(workspace) + "/settings",patch,403);
        call(viewer,"PATCH",base(workspace) + "/settings",patch,403);
        call(stranger,"GET",base(workspace) + "/settings",null,404);
        call(stranger,"PATCH",base(workspace) + "/settings",patch,404);
        MvcResult renamed = call(owner,"PATCH",base(workspace) + "/settings",patch,200);
        assertThat(number(renamed,"$.version")).isEqualTo(version+1);
        call(owner,"PATCH",base(workspace) + "/settings",patch,409);
        call(owner,"PATCH",base(workspace) + "/settings",json("name"," ","version",version+1),400);
        assertThat((String) value(call(owner,"GET","/api/workspaces",null,200),"$[0].name")).isEqualTo("Renamed Workspace");
        for (Client restricted : List.of(member,viewer)) {
            call(restricted,"GET",base(workspace) + "/invitations",null,403);
            call(restricted,"POST",base(workspace) + "/invitations",json("email",email(),"role","MEMBER"),403);
        }
        call(owner,"POST",base(workspace) + "/invitations",json("email",email(),"role","OWNER"),400);
        call(owner,"GET",base(workspace) + "/invitations",null,200);
    }

    @Test
    void anEmailSpecificHashedInvitationCanBeAcceptedByANewAccountOnlyOnce() throws Exception {
        Client owner = account("Invitation Owner"), wrong = account("Wrong Recipient");
        String workspace = workspace(owner), recipient = email();
        MvcResult invited = invite(owner,workspace,recipient,"VIEWER");
        String id = value(invited,"$.id"), token = token(recipient,"You're invited to an Orbit workspace");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM workspace_invitation WHERE id=?",String.class,id)).isEqualTo(digest(token)).isNotEqualTo(token);
        Client anon = anonymous();
        MvcResult preview = call(anon,"GET","/api/invitations/preview?token=" + token,null,200);
        assertThat((String) value(preview,"$.email")).isEqualTo(recipient);
        assertThat((String) value(preview,"$.role")).isEqualTo("VIEWER");
        call(anon,"POST","/api/invitations/accept",json("token",token),401);
        call(wrong,"POST","/api/invitations/accept",json("token",token),403);
        Client joined = account("New Teammate",recipient);
        MvcResult accepted = call(joined,"POST","/api/invitations/accept",json("token",token),200);
        assertThat((String) value(accepted,"$.id")).isEqualTo(workspace);
        assertThat((String) value(accepted,"$.role")).isEqualTo("VIEWER");
        call(joined,"GET",base(workspace) + "/projects",null,200);
        call(joined,"POST",base(workspace) + "/projects",json("name","Forbidden","description","","color","#7c6af2"),403);
        call(joined,"POST","/api/invitations/accept",json("token",token),404);
        call(anon,"GET","/api/invitations/preview?token=" + token,null,404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace_member WHERE workspace_id=? AND user_id=?",Long.class,workspace,joined.id)).isEqualTo(1);
        assertThat((String) value(call(owner,"GET",base(workspace) + "/invitations",null,200),"$[0].status")).isEqualTo("ACCEPTED");
    }

    @Test
    void expiredRevokedAndReplacedInvitationsCannotGrantWorkspaceAccess() throws Exception {
        Client owner = account("Lifecycle Owner"), recipient = account("Lifecycle Recipient");
        String workspace = workspace(owner);
        MvcResult expired = invite(owner,workspace,recipient.email,"MEMBER");
        String expiredToken = token(recipient.email,"You're invited to an Orbit workspace");
        jdbc.update("UPDATE workspace_invitation SET expires_at=? WHERE id=?",OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),(String) value(expired,"$.id"));
        call(anonymous(),"GET","/api/invitations/preview?token=" + expiredToken,null,404);
        call(recipient,"POST","/api/invitations/accept",json("token",expiredToken),404);
        MvcResult revoked = invite(owner,workspace,recipient.email,"VIEWER");
        String revokedToken = token(recipient.email,"You're invited to an Orbit workspace");
        String revokedId = value(revoked,"$.id");
        call(owner,"DELETE",base(workspace) + "/invitations/" + revokedId,null,204);
        call(owner,"DELETE",base(workspace) + "/invitations/" + revokedId,null,409);
        call(recipient,"POST","/api/invitations/accept",json("token",revokedToken),404);
        invite(owner,workspace,recipient.email,"VIEWER");
        String old = token(recipient.email,"You're invited to an Orbit workspace");
        invite(owner,workspace,recipient.email,"MEMBER");
        String replacement = token(recipient.email,"You're invited to an Orbit workspace");
        assertThat(replacement).isNotEqualTo(old);
        call(recipient,"POST","/api/invitations/accept",json("token",old),404);
        call(recipient,"POST","/api/invitations/accept",json("token",replacement),200);
        assertThat(jdbc.queryForObject("SELECT role FROM workspace_member WHERE workspace_id=? AND user_id=?",String.class,workspace,recipient.id)).isEqualTo("MEMBER");
    }

    @Test
    void invitationManagementCannotCrossWorkspacesAndDemotingAnInviterInvalidatesTheirLinks() throws Exception {
        Client first = account("First Owner"), second = account("Second Owner");
        String firstWorkspace = workspace(first), secondWorkspace = workspace(second);
        String target = email();
        MvcResult invitation = invite(first,firstWorkspace,target,"MEMBER");
        String id = value(invitation,"$.id"), token = token(target,"You're invited to an Orbit workspace");
        call(second,"DELETE",base(secondWorkspace) + "/invitations/" + id,null,404);
        call(second,"GET",base(firstWorkspace) + "/invitations",null,404);
        add(first,firstWorkspace,second,"MEMBER");
        call(first,"PATCH",base(firstWorkspace) + "/members/" + second.id,json("role","OWNER"),200);
        call(second,"PATCH",base(firstWorkspace) + "/members/" + first.id,json("role","MEMBER"),200);
        call(anonymous(),"GET","/api/invitations/preview?token=" + token,null,404);
        call(first,"DELETE",base(firstWorkspace) + "/invitations/" + id,null,403);
    }

    @Test
    void assignmentsAndCommentsNotifyRelevantPeopleWithoutSelfOrDuplicateNotifications() throws Exception {
        Client owner = account("Task Owner"), assignee = account("Assignee"), participant = account("Participant");
        String workspace = workspace(owner);
        add(owner,workspace,assignee,"MEMBER"); add(owner,workspace,participant,"MEMBER");
        String project = value(call(owner,"POST",base(workspace) + "/projects",json("name","Notification Project","description","","color","#7c6af2"),201),"$.id");
        String task = value(call(owner,"POST",base(workspace) + "/tasks",json("title","Assigned Task","description","","projectId",project,
                "status","TODO","priority","HIGH","assigneeId",assignee.id),201),"$.id");
        assertThat(count(assignee.id,workspace,task,"ASSIGNED")).isEqualTo(1);
        assertThat(count(owner.id,workspace,task,"ASSIGNED")).isZero();
        call(owner,"POST",base(workspace) + "/tasks/" + task + "/comments",json("body","Owner's comment"),201);
        assertThat(count(assignee.id,workspace,task,"COMMENT")).isEqualTo(1);
        assertThat(count(owner.id,workspace,task,"COMMENT")).isZero();
        call(assignee,"POST",base(workspace) + "/tasks/" + task + "/comments",json("body","Assignee response"),201);
        assertThat(count(owner.id,workspace,task,"COMMENT")).isEqualTo(1);
        assertThat(count(assignee.id,workspace,task,"COMMENT")).isEqualTo(1);
        call(participant,"POST",base(workspace) + "/tasks/" + task + "/comments",json("body","Participant adds context"),201);
        assertThat(count(owner.id,workspace,task,"COMMENT")).isEqualTo(2);
        assertThat(count(assignee.id,workspace,task,"COMMENT")).isEqualTo(2);
        assertThat(count(participant.id,workspace,task,"COMMENT")).isZero();
        call(owner,"DELETE",base(workspace) + "/tasks/" + task + "?version=0",null,204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notification WHERE workspace_id=? AND task_id IS NOT NULL",Long.class,workspace)).isZero();
    }

    @Test
    void notificationReadsAreUserScopedPaginatedAndHiddenAfterMembershipRemoval() throws Exception {
        Client owner = account("Inbox Owner"), member = account("Inbox Member"), outsider = account("Inbox Outsider");
        String workspace = workspace(owner);
        add(owner,workspace,member,"MEMBER");
        MvcResult inbox = call(member,"GET","/api/notifications?page=0&size=1",null,200);
        String notification = value(inbox,"$.items[0].id");
        assertThat(number(inbox,"$.total")).isEqualTo(1);
        assertThat(number(inbox,"$.unreadCount")).isEqualTo(1);
        assertThat(number(call(outsider,"GET","/api/notifications",null,200),"$.total")).isZero();
        call(outsider,"PATCH","/api/notifications/" + notification + "/read",null,404);
        call(owner,"PATCH","/api/notifications/" + notification + "/read",null,404);
        call(member,"PATCH","/api/notifications/" + notification + "/read",null,204);
        assertThat(number(call(member,"GET","/api/notifications",null,200),"$.unreadCount")).isZero();
        call(member,"GET","/api/notifications?size=101",null,400);
        call(member,"GET","/api/notifications?page=-1",null,400);
        call(owner,"PATCH",base(workspace) + "/members/" + member.id,json("role","VIEWER"),200);
        assertThat(number(call(member,"GET","/api/notifications",null,200),"$.unreadCount")).isEqualTo(1);
        call(member,"POST","/api/notifications/read-all",null,204);
        assertThat(number(call(member,"GET","/api/notifications",null,200),"$.unreadCount")).isZero();
        call(owner,"DELETE",base(workspace) + "/members/" + member.id,null,204);
        assertThat(number(call(member,"GET","/api/notifications",null,200),"$.total")).isZero();
        call(member,"PATCH","/api/notifications/" + notification + "/read",null,404);
        call(member,"GET",base(workspace) + "/tasks",null,404);
    }

    @Test
    void concurrentAcceptanceCreatesOnlyOneMembershipAndOneAcceptanceEvent() throws Exception {
        Client owner = account("Concurrent Inviter"), recipient = account("Concurrent Recipient");
        String workspace = workspace(owner);
        invite(owner,workspace,recipient.email,"MEMBER");
        String token = token(recipient.email,"You're invited to an Orbit workspace");
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> acceptAfter(start,recipient.id,token));
            var second = executor.submit(() -> acceptAfter(start,recipient.id,token));
            start.countDown();
            assertThat(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,404);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace_member WHERE workspace_id=? AND user_id=?",Long.class,workspace,recipient.id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_event WHERE workspace_id=? AND actor_id=? AND action='accepted invitation to'",Long.class,workspace,recipient.id)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    private int acceptAfter(CountDownLatch start,String actor,String token) throws InterruptedException {
        start.await();
        try { collaboration.accept(actor,token); return 200; }
        catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
    }
    private long count(String user,String workspace,String task,String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM user_notification WHERE user_id=? AND workspace_id=? AND task_id=? AND type=?",Long.class,user,workspace,task,type); }
    private Client account(String name) throws Exception { return account(name,email()); }
    private Client account(String name,String email) throws Exception {
        Client client = anonymous(); client.email = email;
        client.id = value(call(client,"POST","/api/auth/register",json("name",name,"email",email,"password",PASSWORD),201),"$.id");
        call(client,"POST","/api/auth/verify-email",json("token",token(email,"Verify your Orbit email address")),204);
        call(client,"POST","/api/auth/login",json("email",email,"password",PASSWORD),200); csrf(client); return client;
    }
    private Client anonymous() throws Exception { Client client = new Client(); csrf(client); return client; }
    private String workspace(Client owner) throws Exception { return value(call(owner,"POST","/api/workspaces",json("name","Workspace " + UUID.randomUUID()),201),"$.id"); }
    private void add(Client owner,String workspace,Client member,String role) throws Exception { call(owner,"POST",base(workspace) + "/members",json("email",member.email,"role",role),201); }
    private MvcResult invite(Client owner,String workspace,String email,String role) throws Exception { return call(owner,"POST",base(workspace) + "/invitations",json("email",email,"role",role),201); }
    private void csrf(Client client) throws Exception { MvcResult result = call(client,"GET","/api/auth/csrf",null,200); client.token = value(result,"$.token"); client.header = value(result,"$.headerName"); }
    private String token(String email,String subject) {
        String body = letters.getOrDefault(email,List.of()).stream().filter(letter -> letter.subject().equals(subject)).reduce((a,b) -> b).orElseThrow().body();
        int offset = body.indexOf("?token=") + 7; return body.substring(offset,offset+43);
    }
    private MvcResult call(Client client,String method,String path,String body,int expected) throws Exception {
        var builder = request(HttpMethod.valueOf(method),path);
        if (!client.cookies.isEmpty()) builder.cookie(client.cookies.entrySet().stream().map(entry -> new Cookie(entry.getKey(),entry.getValue())).toArray(Cookie[]::new));
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
        if (!method.equals("GET") && client.token != null) builder.header(client.header,client.token);
        MvcResult result = mvc.perform(builder).andExpect(status().is(expected)).andReturn();
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            String pair = header.split(";",2)[0]; int separator = pair.indexOf('=');
            String name = pair.substring(0,separator),value = pair.substring(separator+1);
            if (value.isEmpty() || header.contains("Max-Age=0")) client.cookies.remove(name); else client.cookies.put(name,value);
        }
        return result;
    }
    private static String base(String workspace) { return "/api/workspaces/" + workspace; }
    private static String email() { return "collab-" + UUID.randomUUID() + "@orbit.test"; }
    private static <T> T value(MvcResult result,String path) throws Exception { return JsonPath.read(result.getResponse().getContentAsString(),path); }
    private static long number(MvcResult result,String path) throws Exception { return ((Number) value(result,path)).longValue(); }
    private static String digest(String raw) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII))); }
    private static String json(Object... entries) {
        StringBuilder json = new StringBuilder("{");
        for(int i=0;i<entries.length;i+=2) { if(i>0) json.append(','); json.append(quote(entries[i].toString())).append(':'); Object value=entries[i+1]; json.append(value instanceof Number ? value.toString() : quote(value.toString())); }
        return json.append('}').toString();
    }
    private static String quote(String value) { return "\"" + value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\""; }
    private static final class Client { String id,email,token,header; final Map<String,String> cookies = new LinkedHashMap<>(); }
}
