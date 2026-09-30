package com.orbit.mail;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Leased claims allow multiple replicas; SMTP delivery remains at least once. */
@Component
public class MailDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(MailDispatcher.class);
    private final JdbcTemplate jdbc;
    private final MailService mail;
    private final JavaMailSender sender;
    private final TransactionTemplate transactions;
    private final MeterRegistry metrics;

    public MailDispatcher(JdbcTemplate jdbc, MailService mail, JavaMailSender sender,
            org.springframework.transaction.PlatformTransactionManager transactionManager, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.sender = sender;
        this.transactions = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
        metrics.gauge("orbit.mail.pending", this, dispatcher -> dispatcher.pending());
    }

    @Scheduled(fixedDelayString = "${orbit.mail.dispatch-delay-ms:5000}", initialDelayString = "${orbit.mail.dispatch-delay-ms:5000}")
    public void dispatch() {
        if (!mail.enabled()) return;
        OffsetDateTime now = now();
        List<String> candidates = jdbc.query("""
                SELECT id FROM mail_outbox WHERE (status='PENDING' AND next_attempt_at<=?)
                OR (status='SENDING' AND claimed_at<?) ORDER BY created_at,id LIMIT 10
                """, (row, index) -> row.getString(1), now, now.minusMinutes(5));
        for (String id : candidates) {
            Delivery delivery = transactions.execute(transaction -> claim(id));
            if (delivery == null) continue;
            try {
                var message = new SimpleMailMessage();
                message.setFrom(mail.from());
                message.setTo(delivery.recipient());
                message.setSubject(delivery.subject());
                message.setText(mail.decrypt(id, delivery.recipient(), delivery.subject(), delivery.encryptedBody()));
                sender.send(message);
                jdbc.update("UPDATE mail_outbox SET status='SENT',sent_at=?,last_error=NULL WHERE id=? AND status='SENDING' AND claimed_at=?",
                        now(), id, delivery.claimedAt());
                metrics.counter("orbit.mail.delivery", "result", "sent").increment();
            } catch (RuntimeException ex) {
                int attempts = delivery.attempts();
                String status = attempts >= 8 ? "FAILED" : "PENDING";
                long delaySeconds = Math.min(21600, 30L << Math.min(attempts - 1, 10));
                jdbc.update("""
                        UPDATE mail_outbox SET status=?,next_attempt_at=?,last_error=?
                        WHERE id=? AND status='SENDING' AND claimed_at=?
                        """, status, now().plusSeconds(delaySeconds), "Delivery failed: " + ex.getClass().getSimpleName(), id, delivery.claimedAt());
                metrics.counter("orbit.mail.delivery", "result", "failed").increment();
                LOG.warn("Email delivery {} failed on attempt {}; inspect outbox status and SMTP configuration.", id, attempts);
            }
        }
        jdbc.update("DELETE FROM mail_outbox WHERE status='SENT' AND sent_at<?", now.minusDays(7));
    }

    private Delivery claim(String id) {
        OffsetDateTime claimedAt = now();
        int changed = jdbc.update("""
                UPDATE mail_outbox SET status='SENDING',attempts=attempts+1,claimed_at=?
                WHERE id=? AND ((status='PENDING' AND next_attempt_at<=?) OR (status='SENDING' AND claimed_at<?))
                """, claimedAt, id, claimedAt, claimedAt.minusMinutes(5));
        if (changed != 1) return null;
        return jdbc.queryForObject("SELECT recipient,subject,encrypted_body,attempts,claimed_at FROM mail_outbox WHERE id=?",
                (row, index) -> new Delivery(row.getString(1), row.getString(2), row.getString(3), row.getInt(4),
                        row.getObject(5, OffsetDateTime.class)), id);
    }

    private double pending() {
        try { return jdbc.queryForObject("SELECT COUNT(*) FROM mail_outbox WHERE status IN ('PENDING','SENDING','FAILED')", Long.class); }
        catch (RuntimeException ex) { return Double.NaN; }
    }

    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private record Delivery(String recipient, String subject, String encryptedBody, int attempts, OffsetDateTime claimedAt) { }
}
