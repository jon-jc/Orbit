CREATE TABLE app_user (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workspace (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workspace_member (
    workspace_id VARCHAR(36) NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
    role VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'MEMBER', 'VIEWER')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX workspace_member_user_idx ON workspace_member(user_id, workspace_id);

CREATE TABLE project (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    color VARCHAR(7) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workspace_id, id)
);
CREATE INDEX project_workspace_idx ON project(workspace_id, status, created_at);

CREATE TABLE task (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    project_id VARCHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(10000) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL CHECK (status IN ('BACKLOG', 'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE')),
    priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    assignee_id VARCHAR(36),
    due_date DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workspace_id, id),
    FOREIGN KEY (workspace_id, project_id) REFERENCES project(workspace_id, id),
    FOREIGN KEY (workspace_id, assignee_id) REFERENCES workspace_member(workspace_id, user_id)
);
CREATE INDEX task_workspace_updated_idx ON task(workspace_id, updated_at, id);
CREATE INDEX task_workspace_status_idx ON task(workspace_id, status);
CREATE INDEX task_workspace_project_idx ON task(workspace_id, project_id);
CREATE INDEX task_workspace_assignee_idx ON task(workspace_id, assignee_id);
CREATE INDEX task_workspace_due_idx ON task(workspace_id, due_date);

CREATE TABLE task_comment (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL,
    task_id VARCHAR(36) NOT NULL,
    author_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (workspace_id, task_id) REFERENCES task(workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX comment_task_idx ON task_comment(workspace_id, task_id, created_at);

CREATE TABLE activity_event (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    actor_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX activity_workspace_created_idx ON activity_event(workspace_id, created_at, id);
