package com.orbit.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Set;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Database versions make password revocation authoritative even when a concurrent login saves late. */
@Component
public class AccountSessionFilter extends OncePerRequestFilter {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> PUBLIC_AUTH = Set.of("/api/auth/csrf", "/api/auth/config", "/api/auth/register", "/api/auth/login",
            "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/verify-email", "/api/auth/resend-verification", "/api/invitations/preview");
    private final CurrentUser current;
    public AccountSessionFilter(CurrentUser current) { this.current = current; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            try { current.view(auth); }
            catch (ResponseStatusException revoked) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                if (request.getServletPath().startsWith("/api/") && !PUBLIC_AUTH.contains(request.getServletPath())) {
                    var body = new LinkedHashMap<String,Object>();
                    body.put("status", 401); body.put("title", "Unauthorized"); body.put("detail", "Your session has expired or been revoked. Sign in again.");
                    body.put("requestId", request.getAttribute("requestId"));
                    response.setStatus(401); response.setContentType("application/problem+json"); response.setCharacterEncoding("UTF-8");
                    JSON.writeValue(response.getWriter(), body); return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    @Bean
    FilterRegistrationBean<AccountSessionFilter> disableAccountSessionContainerRegistration(AccountSessionFilter filter) {
        var registration = new FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }
}
