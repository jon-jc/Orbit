package com.orbit.auth;

import com.jayway.jsonpath.JsonPath;
import com.orbit.mail.MailService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs password, verification, revocation, and token-race guarantees against both databases. */
@SpringBootTest(properties = {"orbit.demo.enabled=false","orbit.auth.rate-limit-enabled=false",
        "orbit.accounts.email-verification-required=true","orbit.mail.enabled=true","orbit.mail.host=127.0.0.1",
        "orbit.mail.dispatch-delay-ms=86400000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AccountSecurityContract {
    private static final String PASSWORD = "OrbitAccount!2026";
    private static final String NEW_PASSWORD = "OrbitNewSecret!2026";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountSecurityService security;
    @Autowired AccountDetailsService details;
    @Autowired JdbcIndexedSessionRepository sessions;
    @MockitoBean MailService mail;
    private final Map<String,List<Letter>> letters = new ConcurrentHashMap<>();
    private record Letter(String subject,String body) {}

    @BeforeEach
    void captureDeliveryIntent() {
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
    void verificationIsRequiredBeforeLoginAndTokensAreHashedPurposeBoundAndSingleUse() throws Exception {
        Client client = anonymous();
        client.email = email();
        MvcResult created = call(client,"POST","/api/auth/register",json("name","Verification Owner","email",client.email,"password",PASSWORD),201);
        client.id = value(created,"$.id");
        assertThat((Boolean) JsonPath.read(created.getResponse().getContentAsString(),"$.emailVerified")).isFalse();
        call(client,"POST","/api/auth/login",json("email",client.email,"password","WrongPassword!2026"),401);
        call(client,"POST","/api/auth/login",json("email",client.email,"password",PASSWORD),403);
        String token = token(client.email,"Verify your Orbit email address");
        String stored = jdbc.queryForObject("SELECT token_hash FROM account_token WHERE user_id=? AND purpose='VERIFY_EMAIL' AND consumed_at IS NULL",String.class,client.id);
        assertThat(stored).isEqualTo(digest(token)).isNotEqualTo(token);
        call(client,"POST","/api/auth/reset-password",json("token",token,"password",NEW_PASSWORD),400);
        call(client,"POST","/api/auth/verify-email",json("token",token),204);
        call(client,"POST","/api/auth/verify-email",json("token",token),400);
        login(client,PASSWORD);
        assertThat((Boolean) JsonPath.read(call(client,"GET","/api/account",null,200).getResponse().getContentAsString(),"$.emailVerified")).isTrue();
    }

    @Test
    void forgotAndResendResponsesDoNotRevealWhichAccountsExistAndNewLinksInvalidateOldOnes() throws Exception {
        Client owner = account("Recovery Owner");
        Client anon = anonymous();
        MvcResult existing = call(anon,"POST","/api/auth/forgot-password",json("email",owner.email),202);
        MvcResult missing = call(anon,"POST","/api/auth/forgot-password",json("email",email()),202);
        assertThat(existing.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
        String oldToken = token(owner.email,"Reset your Orbit password");
        jdbc.update("UPDATE account_token SET created_at=? WHERE token_hash=?",OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2),digest(oldToken));
        call(anon,"POST","/api/auth/forgot-password",json("email",owner.email),202);
        String newToken = token(owner.email,"Reset your Orbit password");
        assertThat(newToken).isNotEqualTo(oldToken);
        call(anon,"POST","/api/auth/reset-password",json("token",oldToken,"password",NEW_PASSWORD),400);
        call(anon,"POST","/api/auth/verify-email",json("token",newToken),400);
        jdbc.update("UPDATE account_token SET expires_at=? WHERE token_hash=?",OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),digest(newToken));
        call(anon,"POST","/api/auth/reset-password",json("token",newToken,"password",NEW_PASSWORD),400);
        call(anon,"POST","/api/auth/reset-password",json("token","A".repeat(43),"password",NEW_PASSWORD),400);
        MvcResult verified = call(anon,"POST","/api/auth/resend-verification",json("email",owner.email),202);
        MvcResult absent = call(anon,"POST","/api/auth/resend-verification",json("email",email()),202);
        assertThat(verified.getResponse().getContentAsString()).isEqualTo(absent.getResponse().getContentAsString());
    }

    @Test
    void rapidRecoveryRequestsKeepExistingLinksUsableWithoutSendingDuplicateEmails() throws Exception {
        Client client = anonymous(); client.email = email();
        client.id = value(call(client,"POST","/api/auth/register",json("name","Cooldown Owner","email",client.email,"password",PASSWORD),201),"$.id");
        String verification = token(client.email,"Verify your Orbit email address");
        call(client,"POST","/api/auth/resend-verification",json("email",client.email),202);
        assertThat(letters.get(client.email).stream().filter(letter -> letter.subject().equals("Verify your Orbit email address"))).hasSize(1);
        call(client,"POST","/api/auth/verify-email",json("token",verification),204);
        call(client,"POST","/api/auth/forgot-password",json("email",client.email),202);
        String original = token(client.email,"Reset your Orbit password");
        call(client,"POST","/api/auth/forgot-password",json("email",client.email),202);
        assertThat(letters.get(client.email).stream().filter(letter -> letter.subject().equals("Reset your Orbit password"))).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NULL FROM account_token WHERE token_hash=?",Boolean.class,digest(original))).isTrue();
        call(client,"POST","/api/auth/reset-password",json("token",original,"password",NEW_PASSWORD),204);
        String ownerEmail = client.email;
        client = anonymous(); client.email = ownerEmail;
        call(client,"POST","/api/auth/forgot-password",json("email",ownerEmail),202);
        assertThat(letters.get(ownerEmail).stream().filter(letter -> letter.subject().equals("Reset your Orbit password"))).hasSize(1);
        jdbc.update("UPDATE account_token SET created_at=? WHERE token_hash=?",OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2),digest(original));
        call(client,"POST","/api/auth/forgot-password",json("email",ownerEmail),202);
        String replacement = token(ownerEmail,"Reset your Orbit password");
        assertThat(replacement).isNotEqualTo(original);
        assertThat(letters.get(ownerEmail).stream().filter(letter -> letter.subject().equals("Reset your Orbit password"))).hasSize(2);
        call(client,"POST","/api/auth/reset-password",json("token",replacement,"password",PASSWORD),204);
    }

    @Test
    void passwordChangeChecksCurrentPasswordAndRevokesEveryDatabaseSession() throws Exception {
        Client first = account("Password Owner");
        Client second = anonymous(); second.email = first.email; login(second,PASSWORD);
        call(first,"POST","/api/account/password",json("currentPassword","WrongPassword!2026","newPassword",NEW_PASSWORD),400);
        call(second,"GET","/api/account",null,200);
        call(first,"POST","/api/account/password",json("currentPassword",PASSWORD,"newPassword","short"),400);
        call(first,"POST","/api/account/password",json("currentPassword",PASSWORD,"newPassword",NEW_PASSWORD),204);
        call(first,"GET","/api/account",null,401);
        call(second,"GET","/api/account",null,401);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM app_user WHERE id=?",Long.class,first.id)).isEqualTo(1);
        Client fresh = anonymous(); fresh.email = first.email;
        call(fresh,"POST","/api/auth/login",json("email",first.email,"password",PASSWORD),401);
        login(fresh,NEW_PASSWORD);
    }

    @Test
    void resetRevokesSessionsAndRejectsAStalePrincipalSavedAfterTheReset() throws Exception {
        Client owner = account("Late Login Owner");
        AccountPrincipal stale = (AccountPrincipal) details.loadUserByUsername(owner.email);
        Client anon = anonymous();
        call(anon,"POST","/api/auth/forgot-password",json("email",owner.email),202);
        String token = token(owner.email,"Reset your Orbit password");
        call(anon,"POST","/api/auth/reset-password",json("token",token,"password",NEW_PASSWORD),204);
        call(owner,"GET","/api/workspaces",null,401);
        var repository = publicSessionRepository(sessions);
        Session late = repository.createSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(stale,null,stale.getAuthorities()));
        late.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
        repository.save(late);
        String cookie = Base64.getEncoder().encodeToString(late.getId().getBytes(StandardCharsets.UTF_8));
        mvc.perform(request(HttpMethod.GET,"/api/account").cookie(new Cookie("ORBIT_SESSION",cookie))).andExpect(status().isUnauthorized());
        assertThat(repository.findById(late.getId())).isNull();
        anon = anonymous();
        call(anon,"POST","/api/auth/reset-password",json("token",token,"password",PASSWORD),400);
        Client fresh = anonymous(); fresh.email = owner.email; login(fresh,NEW_PASSWORD);
    }

    @Test
    void profileEditsPersistWithoutChangingTheEmailAddress() throws Exception {
        Client owner = account("Original Name");
        call(owner,"PATCH","/api/account",json("name"," "),400);
        call(owner,"PATCH","/api/account",json("name","Updated Name"),200);
        MvcResult profile = call(owner,"GET","/api/auth/me",null,200);
        assertThat((String) value(profile,"$.name")).isEqualTo("Updated Name");
        assertThat((String) value(profile,"$.email")).isEqualTo(owner.email);
        assertThat(jdbc.queryForObject("SELECT email FROM app_user WHERE id=?",String.class,owner.id)).isEqualTo(owner.email);
    }

    @Test
    void sessionRevocationIsScopedAndCanSignOutAnotherDeviceOrTheCurrentDevice() throws Exception {
        Client owner = account("Sessions Owner");
        Client second = anonymous(); second.email = owner.email; login(second,PASSWORD);
        Client other = account("Other Account");
        MvcResult otherSessions = call(other,"GET","/api/account/sessions",null,200);
        String foreign = value(otherSessions,"$[0].id");
        call(owner,"DELETE","/api/account/sessions/" + foreign,null,404);
        MvcResult listed = call(owner,"GET","/api/account/sessions",null,200);
        List<Map<String,Object>> active = JsonPath.read(listed.getResponse().getContentAsString(),"$");
        assertThat(active).hasSize(2);
        String current = active.stream().filter(row -> Boolean.TRUE.equals(row.get("current"))).map(row -> (String) row.get("id")).findFirst().orElseThrow();
        String remote = active.stream().filter(row -> Boolean.FALSE.equals(row.get("current"))).map(row -> (String) row.get("id")).findFirst().orElseThrow();
        call(owner,"DELETE","/api/account/sessions/" + remote,null,204);
        call(second,"GET","/api/account",null,401);
        call(owner,"GET","/api/account",null,200);
        call(other,"GET","/api/account",null,200);
        call(owner,"DELETE","/api/account/sessions/" + current,null,204);
        call(owner,"GET","/api/account",null,401);
    }

    @Test
    void concurrentConsumptionAllowsOnlyOnePasswordResetAndOneVersionIncrement() throws Exception {
        Client owner = account("Concurrent Owner");
        security.forgotPassword(owner.email);
        String token = token(owner.email,"Reset your Orbit password");
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var outcomes = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (String password : List.of(NEW_PASSWORD,"AnotherSecret!2026")) outcomes.add(executor.submit(() -> {
                start.await();
                try { security.resetPassword(token,password); return 204; }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
            }));
            start.countDown();
            assertThat(List.of(outcomes.get(0).get(30,TimeUnit.SECONDS),outcomes.get(1).get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,400);
            assertThat(jdbc.queryForObject("SELECT auth_version FROM app_user WHERE id=?",Long.class,owner.id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_security_event WHERE user_id=? AND event_type='PASSWORD_RESET'",Long.class,owner.id)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    private Client account(String name) throws Exception {
        Client client = anonymous(); client.email = email();
        client.id = value(call(client,"POST","/api/auth/register",json("name",name,"email",client.email,"password",PASSWORD),201),"$.id");
        call(client,"POST","/api/auth/verify-email",json("token",token(client.email,"Verify your Orbit email address")),204);
        login(client,PASSWORD); return client;
    }
    private Client anonymous() throws Exception { Client client = new Client(); csrf(client); return client; }
    private void login(Client client,String password) throws Exception { call(client,"POST","/api/auth/login",json("email",client.email,"password",password),200); csrf(client); }
    private void csrf(Client client) throws Exception {
        MvcResult response = call(client,"GET","/api/auth/csrf",null,200);
        client.token = value(response,"$.token"); client.header = value(response,"$.headerName");
    }
    private String token(String email,String subject) {
        List<Letter> sent = letters.getOrDefault(email,List.of());
        String body = sent.stream().filter(letter -> letter.subject().equals(subject)).reduce((a,b) -> b).orElseThrow().body();
        int start = body.indexOf("?token=") + 7;
        return body.substring(start,start+43);
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
    private static String email() { return "account-" + UUID.randomUUID() + "@orbit.test"; }
    @SuppressWarnings("unchecked")
    private static SessionRepository<Session> publicSessionRepository(JdbcIndexedSessionRepository repository) {
        return (SessionRepository<Session>) (SessionRepository<?>) repository;
    }
    private static <T> T value(MvcResult result,String path) throws Exception { return JsonPath.read(result.getResponse().getContentAsString(),path); }
    private static String digest(String raw) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
    private static String json(Object... entries) {
        StringBuilder json = new StringBuilder("{");
        for (int i=0;i<entries.length;i+=2) { if(i>0) json.append(','); json.append(quote(entries[i].toString())).append(':').append(quote(entries[i+1].toString())); }
        return json.append('}').toString();
    }
    private static String quote(String value) { return "\"" + value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\""; }
    private static final class Client { String id,email,token,header; final Map<String,String> cookies = new LinkedHashMap<>(); }
}
