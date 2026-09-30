package com.orbit.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {
    private final JdbcTemplate jdbc;
    private final AccountProperties properties;
    public CurrentUser(JdbcTemplate jdbc, AccountProperties properties) { this.jdbc = jdbc; this.properties = properties; }
    public record UserView(String id, String name, String email, boolean emailVerified) {}
    public String id(Authentication authentication) { return view(authentication).id(); }
    public UserView view(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        AccountPrincipal principal = principal(authentication);
        return jdbc.query("SELECT id,display_name,email,email_verified FROM app_user WHERE id=? AND auth_version=?",
                (rs,n) -> new UserView(rs.getString("id"),rs.getString("display_name"),rs.getString("email"),rs.getBoolean("email_verified")),
                principal.id(), principal.authVersion()).stream().findFirst()
                .map(user -> {
                    if (properties.isEmailVerificationRequired() && !user.emailVerified())
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Verify your email address before signing in.");
                    return user;
                }).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Your session has been revoked. Sign in again."));
    }
    public AccountPrincipal principal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof AccountPrincipal principal))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to continue.");
        return principal;
    }
    public UserView byEmail(String email) {
        return jdbc.query("SELECT id, display_name, email,email_verified FROM app_user WHERE email = ?",
                (rs, n) -> new UserView(rs.getString("id"), rs.getString("display_name"), rs.getString("email"),rs.getBoolean("email_verified")), email)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable."));
    }
}
