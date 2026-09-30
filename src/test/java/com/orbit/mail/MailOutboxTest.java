package com.orbit.mail;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:orbit-mail;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "orbit.demo.enabled=false",
        "orbit.mail.enabled=true", "orbit.mail.host=127.0.0.1", "orbit.mail.from=orbit@example.test",
        "orbit.mail.dispatch-delay-ms=86400000"})
@ActiveProfiles("test")
class MailOutboxTest {
    @Autowired MailService mail;
    @Autowired MailDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean JavaMailSender sender;

    @DynamicPropertySource
    static void key(DynamicPropertyRegistry properties) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        properties.add("orbit.mail.encryption-key", () -> encoded);
    }

    @BeforeEach
    void cleanQueue() {
        jdbc.update("DELETE FROM mail_outbox");
        reset(sender);
    }

    @Test
    void rollbackDoesNotPublishAnEmailAndBodiesAreEncryptedUntilDelivery() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            mail.enqueue("person@example.test", "Verify email", "secret-action-token");
            transaction.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_outbox", Long.class)).isZero();
        mail.enqueue("person@example.test", "Verify email", "secret-action-token");
        assertThat(jdbc.queryForObject("SELECT encrypted_body FROM mail_outbox", String.class))
                .doesNotContain("secret-action-token");
        dispatcher.dispatch();
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getText()).isEqualTo("secret-action-token");
        assertThat(message.getValue().getTo()).containsExactly("person@example.test");
        assertThat(jdbc.queryForObject("SELECT status FROM mail_outbox", String.class)).isEqualTo("SENT");
    }

    @Test
    void failedSmtpIsRetriedAndACommittedDeliveryIsNotSentTwice() {
        mail.enqueue("person@example.test", "Reset password", "reset-token");
        doThrow(new MailSendException("Simulated outage")).doNothing().when(sender).send(any(SimpleMailMessage.class));
        dispatcher.dispatch();
        assertThat(jdbc.queryForObject("SELECT status FROM mail_outbox", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM mail_outbox", Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE mail_outbox SET next_attempt_at=?", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        dispatcher.dispatch();
        dispatcher.dispatch();
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
        assertThat(jdbc.queryForObject("SELECT status FROM mail_outbox", String.class)).isEqualTo("SENT");
    }

    @Test
    void replicasClaimAnEmailOnceAndReclaimAnExpiredLease() throws Exception {
        mail.enqueue("person@example.test", "Invitation", "invite-token");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(dispatcher::dispatch);
            var second = pool.submit(dispatcher::dispatch);
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
        clearInvocations(sender);
        mail.enqueue("another@example.test", "Invitation", "another-token");
        jdbc.update("UPDATE mail_outbox SET status='SENDING',claimed_at=? WHERE status='PENDING'",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        dispatcher.dispatch();
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void tamperedEnvelopeIsNotDeliveredAndFailuresAreBounded() {
        mail.enqueue("person@example.test", "Reset password", "reset-token");
        jdbc.update("UPDATE mail_outbox SET subject='Tampered subject',attempts=7");
        dispatcher.dispatch();
        verify(sender, times(0)).send(any(SimpleMailMessage.class));
        assertThat(jdbc.queryForObject("SELECT status FROM mail_outbox", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT last_error FROM mail_outbox", String.class))
                .doesNotContain("reset-token");
    }
}
