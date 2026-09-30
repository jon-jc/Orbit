package com.orbit.mail;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Email intent commits with the domain change; raw action tokens are encrypted at rest. */
@Service
public class MailService {
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final String from;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public MailService(JdbcTemplate jdbc,
            @Value("${orbit.mail.enabled:false}") boolean enabled,
            @Value("${orbit.mail.from:}") String from,
            @Value("${orbit.mail.encryption-key:}") String encodedKey) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.from = from;
        if (enabled) {
            validateAddress(from);
            try {
                byte[] raw = Base64.getDecoder().decode(encodedKey);
                if (raw.length != 32) throw new IllegalArgumentException("Wrong key length");
                this.key = new SecretKeySpec(raw, "AES");
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Enabled email requires a Base64-encoded 32-byte encryption key.");
            }
        } else {
            this.key = null;
        }
    }

    @Transactional
    public void enqueue(String to, String subject, String body) {
        if (!enabled) return;
        validateAddress(to);
        if (subject == null || subject.isBlank() || subject.length() > 200
                || subject.contains("\r") || subject.contains("\n")) {
            throw new IllegalArgumentException("Invalid email subject.");
        }
        if (body == null || body.getBytes(StandardCharsets.UTF_8).length > 65536) {
            throw new IllegalArgumentException("Email body exceeds its limit.");
        }
        String id = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO mail_outbox(id,recipient,subject,encrypted_body,status,created_at,next_attempt_at)
                VALUES(?,?,?,?,'PENDING',?,?)
                """, id, to, subject, encrypt(id, to, subject, body), now, now);
    }

    boolean enabled() { return enabled; }
    String from() { return from; }

    String decrypt(String id, String to, String subject, String encoded) {
        try {
            byte[] envelope = Base64.getDecoder().decode(encoded);
            if (envelope.length < 28) throw new GeneralSecurityException("Invalid envelope");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, envelope, 0, 12));
            cipher.updateAAD(aad(id, to, subject));
            return new String(cipher.doFinal(envelope, 12, envelope.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Email decryption failed. Verify the configured outbox key.", ex);
        }
    }

    private String encrypt(String id, String to, String subject, String body) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(id, to, subject));
            byte[] encrypted = cipher.doFinal(body.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, envelope, 0, nonce.length);
            System.arraycopy(encrypted, 0, envelope, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Email encryption is unavailable.", ex);
        }
    }

    private static byte[] aad(String id, String to, String subject) {
        return (id + "\n" + to + "\n" + subject).getBytes(StandardCharsets.UTF_8);
    }

    private static void validateAddress(String address) {
        try {
            if (address == null || address.length() > 254 || address.contains("\r") || address.contains("\n")) {
                throw new AddressException("Invalid address");
            }
            InternetAddress parsed = new InternetAddress(address, true);
            parsed.validate();
            if (!address.equals(parsed.getAddress())) throw new AddressException("Use a single address");
        } catch (AddressException ex) {
            throw new IllegalArgumentException("Use a valid single email address.");
        }
    }
}
