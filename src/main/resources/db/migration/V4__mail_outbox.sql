CREATE TABLE mail_outbox (
    id VARCHAR(36) PRIMARY KEY,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    encrypted_body TEXT NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(200)
);
CREATE INDEX mail_outbox_delivery_idx ON mail_outbox(status, next_attempt_at);
