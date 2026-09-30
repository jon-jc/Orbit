package com.orbit.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountSecurityService security;
    private final CurrentUser current;
    public AccountController(AccountSecurityService security, CurrentUser current) { this.security = security; this.current = current; }
    public record Profile(@NotBlank @Size(max=100) @Pattern(regexp="^[^\\x00]*$") String name) {}
    public record Password(@NotBlank @Size(max=72) String currentPassword, @NotBlank @Size(min=12,max=72) String newPassword) {}

    @GetMapping
    public CurrentUser.UserView profile(Authentication auth) { return current.view(auth); }

    @PatchMapping
    public CurrentUser.UserView updateProfile(Authentication auth, @Valid @RequestBody Profile input) {
        return security.updateProfile(current.principal(auth),input.name());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication auth, @Valid @RequestBody Password input, HttpServletRequest request) {
        security.changePassword(current.principal(auth),input.currentPassword(),input.newPassword());
        invalidateCurrentSession(request);
    }

    @PostMapping("/verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,String> resendVerification(Authentication auth) {
        security.resendVerification(current.principal(auth));
        return Map.of("message","If verification is needed, a new link will be sent.");
    }

    @GetMapping("/sessions")
    public List<AccountSecurityService.SessionView> sessions(Authentication auth, HttpServletRequest request) {
        return security.sessions(current.principal(auth),currentSessionId(request));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(Authentication auth, @PathVariable String id, HttpServletRequest request) {
        if (security.revokeSession(current.principal(auth),id,currentSessionId(request))) invalidateCurrentSession(request);
    }

    static String currentSessionId(HttpServletRequest request) {
        var session = request.getSession(false); return session == null ? null : session.getId();
    }
    static void invalidateCurrentSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false); if (session != null) session.invalidate();
    }
}
