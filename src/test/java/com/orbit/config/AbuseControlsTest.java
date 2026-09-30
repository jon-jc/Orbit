package com.orbit.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class AbuseControlsTest {
    @Test void excessiveDeclaredPayloadIsRejectedBeforeTheControllerRuns() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/auth/register");
        request.setContent(new byte[65537]);
        var response=new MockHttpServletResponse();
        var invoked=new AtomicInteger();
        new RequestBodyLimitFilter().doFilter(request,response,(req,res) -> invoked.incrementAndGet());
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(invoked.get()).isZero();
    }
    @Test void chunkedPayloadCannotBypassTheLimitAndExactLimitIsAccepted() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/workspaces");
        request.setContent(new byte[65537]);
        var unknownLength=new HttpServletRequestWrapper(request) {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        assertThatThrownBy(() -> new RequestBodyLimitFilter().doFilter(unknownLength,new MockHttpServletResponse(),
                (req,res) -> ((HttpServletRequest)req).getInputStream().readAllBytes()))
                .isInstanceOf(RequestBodyLimitFilter.PayloadTooLargeException.class);
        request.setContent(new byte[65536]);
        var received=new AtomicInteger();
        new RequestBodyLimitFilter().doFilter(unknownLength,new MockHttpServletResponse(),
                (req,res) -> received.set(((HttpServletRequest)req).getInputStream().readAllBytes().length));
        assertThat(received.get()).isEqualTo(65536);
    }
    @Test void signInLimitIsAtomicAndRecoversAfterTheWindow() throws Exception {
        var clock=new MutableClock();
        var filter=new AuthRateLimitFilter(true,clock);
        var passed=new AtomicInteger();
        var executor=Executors.newFixedThreadPool(8);
        try {
            var jobs=new ArrayList<Callable<Integer>>();
            for(int i=0;i<40;i++) jobs.add(() -> {
                var response=new MockHttpServletResponse();
                filter.doFilter(login("127.0.0.1"),response,(req,res) -> passed.incrementAndGet());
                return response.getStatus();
            });
            var results=executor.invokeAll(jobs);
            assertThat(passed.get()).isEqualTo(20);
            long rejected=0;
            for(var result:results) if(result.get()==429) rejected++;
            assertThat(rejected).isEqualTo(20);
            var other=new MockHttpServletResponse();
            filter.doFilter(login("127.0.0.2"),other,(req,res) -> passed.incrementAndGet());
            assertThat(other.getStatus()).isEqualTo(200);
            clock.millis=61000;
            var after=new MockHttpServletResponse();
            filter.doFilter(login("127.0.0.1"),after,(req,res) -> passed.incrementAndGet());
            assertThat(after.getStatus()).isEqualTo(200);
            assertThat(passed.get()).isEqualTo(22);
        } finally { executor.shutdownNow(); }
    }
    private static MockHttpServletRequest login(String address) {
        var request=new MockHttpServletRequest("POST","/api/auth/login");
        request.setServletPath("/api/auth/login"); request.setRemoteAddr(address);
        request.setContent("{}".getBytes(StandardCharsets.UTF_8)); return request;
    }
    private static final class MutableClock extends Clock {
        long millis;
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
