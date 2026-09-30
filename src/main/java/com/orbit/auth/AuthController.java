package com.orbit.auth;

import java.util.Locale;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final CurrentUser current;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    private final boolean demoEnabled;
    public AuthController(AccountService accounts, CurrentUser current, AuthenticationManager authenticationManager,
                          SecurityContextRepository contexts, CsrfTokenRepository csrf,
                          @Value("${orbit.demo.enabled:false}") boolean demoEnabled) {
        this.accounts = accounts; this.current = current; this.authenticationManager = authenticationManager;
        this.contexts = contexts; this.csrf = csrf; this.demoEnabled = demoEnabled;
    }
    public record Register(@NotBlank @Size(max=100) @Pattern(regexp="^[^\\x00]*$") String name, @NotBlank @Email @Size(max=254) String email,
                           @NotBlank @Size(min=12,max=72) String password) {}
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/config") public Map<String,Boolean> config() { return Map.of("demoEnabled",demoEnabled); }
    @GetMapping("/me") public CurrentUser.UserView me(Authentication authentication) { return current.view(authentication); }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public CurrentUser.UserView register(@Valid @RequestBody Register input) { return accounts.register(input.name(),input.email(),input.password()); }
    @PostMapping("/login")
    public CurrentUser.UserView login(@Valid @RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must fit within 72 UTF-8 bytes.");
        Authentication auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                input.email().strip().toLowerCase(Locale.ROOT), input.password()));
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(auth, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        csrf.saveToken(null, request, response);
        return current.view(auth);
    }
}
