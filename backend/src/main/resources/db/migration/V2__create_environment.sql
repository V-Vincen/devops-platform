CREATE TABLE environment (
    id           BIGINT PRIMARY KEY,
    project_id   BIGINT       NOT NULL,
    code         VARCHAR(32)  NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    namespace    VARCHAR(63)  NOT NULL,
    cluster_name VARCHAR(64),
    status       VARCHAR(20)  NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_environment_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT ck_environment_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE environment IS '项目运行环境';
COMMENT ON COLUMN environment.project_id IS '所属项目 ID';
COMMENT ON COLUMN environment.code IS '项目内唯一的环境编码';
COMMENT ON COLUMN environment.namespace IS 'Kubernetes Namespace';
COMMENT ON COLUMN environment.cluster_name IS 'Kubernetes 集群名称';
COMMENT ON COLUMN environment.status IS '环境状态：ACTIVE 或 DISABLED';

CREATE UNIQUE INDEX uk_environment_project_code_active
    ON environment (project_id, code)
    WHERE deleted = FALSE;

CREATE INDEX idx_environment_project_status_created_at
    ON environment (project_id, status, created_at DESC);
