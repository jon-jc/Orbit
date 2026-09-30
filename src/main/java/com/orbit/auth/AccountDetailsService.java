package com.orbit.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    public AccountDetailsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return jdbc.query("SELECT id,email,password_hash,auth_version,email_verified FROM app_user WHERE email=?",
                (row, index) -> new AccountPrincipal(row.getString("id"), row.getString("email"), row.getString("password_hash"),
                        row.getLong("auth_version"), row.getBoolean("email_verified")), email)
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
    }
}
