CREATE TABLE saved_task_view (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    label VARCHAR(80) NOT NULL,
    query_text VARCHAR(200),
    status VARCHAR(20) CHECK (status IN ('BACKLOG', 'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE')),
    priority VARCHAR(20) CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    project_id VARCHAR(36) REFERENCES project(id) ON DELETE SET NULL,
    assignee_id VARCHAR(36) REFERENCES app_user(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (workspace_id, user_id) REFERENCES workspace_member(workspace_id, user_id) ON DELETE CASCADE
);
CREATE INDEX saved_task_view_owner_idx ON saved_task_view(workspace_id, user_id, updated_at);
