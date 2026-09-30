package com.orbit.auth;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "orbit.accounts")
public class AccountProperties {
    private final Environment environment;
    private boolean registrationEnabled = true;
    private boolean emailVerificationRequired;
    private String publicBaseUrl = "http://127.0.0.1:8080";
    private Duration resetTokenTtl = Duration.ofMinutes(30);
    private Duration verificationTokenTtl = Duration.ofHours(24);
    private Duration tokenSendCooldown = Duration.ofMinutes(1);

    @Autowired
    public AccountProperties(Environment environment) { this.environment = environment; }

    @PostConstruct
    void validate() {
        URI base;
        try { base = URI.create(publicBaseUrl); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("orbit.accounts.public-base-url must be an absolute HTTP(S) origin.", ex); }
        boolean http = "http".equalsIgnoreCase(base.getScheme());
        boolean https = "https".equalsIgnoreCase(base.getScheme());
        if ((!http && !https) || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null
                || (base.getPath() != null && !base.getPath().isEmpty() && !"/".equals(base.getPath()))) {
            throw new IllegalStateException("orbit.accounts.public-base-url must be an absolute HTTP(S) origin without credentials, path, query, or fragment.");
        }
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod") && !https) {
            throw new IllegalStateException("Production account links require an HTTPS orbit.accounts.public-base-url.");
        }
        if (emailVerificationRequired && registrationEnabled && !environment.getProperty("orbit.mail.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Email verification requires orbit.mail.enabled=true and a working SMTP configuration.");
        }
        validateTtl(resetTokenTtl, "reset-token-ttl");
        validateTtl(verificationTokenTtl, "verification-token-ttl");
        if (tokenSendCooldown == null || tokenSendCooldown.compareTo(Duration.ofSeconds(1)) < 0
                || tokenSendCooldown.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalStateException("orbit.accounts.token-send-cooldown must be between one second and one hour.");
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    private static void validateTtl(Duration ttl, String property) {
        if (ttl == null || ttl.compareTo(Duration.ofMinutes(1)) < 0 || ttl.compareTo(Duration.ofDays(7)) > 0)
            throw new IllegalStateException("orbit.accounts." + property + " must be between one minute and seven days.");
    }

    public boolean isRegistrationEnabled() { return registrationEnabled; }
    public void setRegistrationEnabled(boolean value) { registrationEnabled = value; }
    public boolean isEmailVerificationRequired() { return emailVerificationRequired; }
    public void setEmailVerificationRequired(boolean value) { emailVerificationRequired = value; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String value) { publicBaseUrl = value; }
    public Duration getResetTokenTtl() { return resetTokenTtl; }
    public void setResetTokenTtl(Duration value) { resetTokenTtl = value; }
    public Duration getVerificationTokenTtl() { return verificationTokenTtl; }
    public void setVerificationTokenTtl(Duration value) { verificationTokenTtl = value; }
    public Duration getTokenSendCooldown() { return tokenSendCooldown; }
    public void setTokenSendCooldown(Duration value) { tokenSendCooldown = value; }
}
