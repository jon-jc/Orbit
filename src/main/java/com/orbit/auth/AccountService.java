package com.orbit.auth;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    public AccountService(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }
    @Transactional
    public CurrentUser.UserView register(String name, String email, String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must fit within 72 UTF-8 bytes.");
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        String normalizedName = name.strip();
        if (normalizedName.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO app_user(id,email,display_name,password_hash,created_at) VALUES (?,?,?,?,?)",
                    id, normalizedEmail, normalizedName, encoder.encode(password), OffsetDateTime.now());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        return new CurrentUser.UserView(id, normalizedName, normalizedEmail);
    }
}
