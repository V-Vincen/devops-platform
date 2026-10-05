CREATE TABLE sys_user_project (
    id         BIGINT PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    project_id BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted    BOOLEAN   NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_sys_user_project_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_project_project
        FOREIGN KEY (project_id) REFERENCES project (id)
);

COMMENT ON TABLE sys_user_project IS '开发人员与项目的访问授权';
COMMENT ON COLUMN sys_user_project.user_id IS '被授权的平台用户';
COMMENT ON COLUMN sys_user_project.project_id IS '被授权访问的项目';

CREATE UNIQUE INDEX uk_sys_user_project_active
    ON sys_user_project (user_id, project_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_sys_user_project_project
    ON sys_user_project (project_id, deleted);

CREATE INDEX idx_sys_user_project_user
    ON sys_user_project (user_id, deleted);
