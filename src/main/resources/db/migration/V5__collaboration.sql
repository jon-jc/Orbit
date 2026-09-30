ALTER TABLE workspace ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE workspace_invitation (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    email VARCHAR(254) NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('MEMBER', 'VIEWER')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    invited_by VARCHAR(36) NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX invitation_workspace_idx ON workspace_invitation(workspace_id, created_at);
CREATE INDEX invitation_actor_idx ON workspace_invitation(invited_by, created_at);

CREATE TABLE user_notification (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
    workspace_id VARCHAR(36) NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    task_id VARCHAR(36) REFERENCES task(id) ON DELETE SET NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX notification_user_idx ON user_notification(user_id, created_at, id);
