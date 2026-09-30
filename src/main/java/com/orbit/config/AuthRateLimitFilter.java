package com.orbit.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded per-instance abuse control. In production supplement with a gateway-wide limiter. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/login","/api/auth/register",
            "/api/auth/forgot-password","/api/auth/reset-password","/api/auth/verify-email","/api/auth/resend-verification",
            "/api/account/password","/api/account/verification");
    private final Map<String, Window> attempts = new HashMap<>();
    private final boolean enabled;
    private final Clock clock;
    private record Window(long endsAt, int count) {}
    @Autowired
    public AuthRateLimitFilter(@Value("${orbit.auth.rate-limit-enabled:true}") boolean enabled) { this(enabled,Clock.systemUTC()); }
    AuthRateLimitFilter(boolean enabled, Clock clock) { this.enabled=enabled; this.clock=clock; }
    private synchronized boolean allow(String key) {
        long now=clock.millis();
        attempts.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
        Window window=attempts.get(key);
        if (window == null) {
            if (attempts.size() >= 10000) return false;
            attempts.put(key,new Window(now+60000,1)); return true;
        }
        if (window.count() >= 20) return false;
        attempts.put(key,new Window(window.endsAt(),window.count()+1)); return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getServletPath();
        if (enabled && "POST".equals(request.getMethod()) && LIMITED_PATHS.contains(path)
                && !allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After","60");
            JsonErrors.write(response,429,"Too many requests","Too many sign-in attempts. Try again in a minute."); return;
        }
        chain.doFilter(request,response);
    }
    @Bean FilterRegistrationBean<AuthRateLimitFilter> disableContainerRegistration(AuthRateLimitFilter filter) {
        var registration=new FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }
}
