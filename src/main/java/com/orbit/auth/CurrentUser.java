package com.orbit.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {
    private final JdbcTemplate jdbc;
    public CurrentUser(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record UserView(String id, String name, String email) {}
    public String id(Authentication authentication) { return view(authentication).id(); }
    public UserView view(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        return byEmail(authentication.getName());
    }
    public UserView byEmail(String email) {
        return jdbc.query("SELECT id, display_name, email FROM app_user WHERE email = ?",
                (rs, n) -> new UserView(rs.getString("id"), rs.getString("display_name"), rs.getString("email")), email)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable."));
    }
}
