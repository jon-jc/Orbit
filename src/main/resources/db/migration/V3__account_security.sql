ALTER TABLE app_user ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN password_changed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE account_token (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    purpose VARCHAR(16) NOT NULL CHECK (purpose IN ('RESET_PASSWORD', 'VERIFY_EMAIL')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX account_token_user_purpose_idx ON account_token(user_id, purpose, consumed_at);
CREATE INDEX account_token_expiry_idx ON account_token(expires_at);

CREATE TABLE account_security_event (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX account_security_event_user_idx ON account_security_event(user_id, occurred_at);
