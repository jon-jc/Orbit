package com.orbit.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Versioned identity persisted in Spring Session. Password hashes are never serialized. */
public final class AccountPrincipal implements UserDetails, CredentialsContainer, Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private final String id;
    private final String email;
    private final long authVersion;
    private final boolean emailVerified;
    private transient String password;

    public AccountPrincipal(String id, String email, String password, long authVersion, boolean emailVerified) {
        this.id = id; this.email = email; this.password = password; this.authVersion = authVersion; this.emailVerified = emailVerified;
    }

    public String id() { return id; }
    public long authVersion() { return authVersion; }
    public boolean emailVerified() { return emailVerified; }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_USER")); }
    @Override public void eraseCredentials() { password = null; }
}
