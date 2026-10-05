CREATE TABLE project (
    id          BIGINT PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(500),
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE project IS 'DevOps 平台项目';
COMMENT ON COLUMN project.code IS '项目编码，平台内唯一';
COMMENT ON COLUMN project.status IS '项目状态：ACTIVE 或 DISABLED';

CREATE UNIQUE INDEX uk_project_code_active
    ON project (code)
    WHERE deleted = FALSE;

CREATE INDEX idx_project_status_created_at
    ON project (status, created_at DESC);
