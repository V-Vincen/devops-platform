CREATE TABLE service (
    id               BIGINT PRIMARY KEY,
    project_id       BIGINT       NOT NULL,
    service_code     VARCHAR(64)  NOT NULL,
    service_name     VARCHAR(128) NOT NULL,
    repository_url   VARCHAR(500) NOT NULL,
    branch_name      VARCHAR(128) NOT NULL,
    build_type       VARCHAR(32)  NOT NULL,
    image_repository VARCHAR(500) NOT NULL,
    port             INTEGER      NOT NULL,
    replicas         INTEGER      NOT NULL DEFAULT 1,
    cpu_limit        VARCHAR(32),
    memory_limit     VARCHAR(32),
    status           VARCHAR(20)  NOT NULL,
    description      VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_service_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT ck_service_port
        CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_service_replicas
        CHECK (replicas BETWEEN 1 AND 20),
    CONSTRAINT ck_service_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE service IS '项目微服务';
COMMENT ON COLUMN service.project_id IS '所属项目 ID';
COMMENT ON COLUMN service.service_code IS '项目内唯一的微服务编码';
COMMENT ON COLUMN service.repository_url IS 'Gitee 等代码仓库地址';
COMMENT ON COLUMN service.branch_name IS '构建分支';
COMMENT ON COLUMN service.build_type IS '构建类型，例如 MAVEN、NODE_JS';
COMMENT ON COLUMN service.image_repository IS 'Docker 镜像仓库地址';
COMMENT ON COLUMN service.port IS '容器服务端口';
COMMENT ON COLUMN service.replicas IS '默认副本数量';
COMMENT ON COLUMN service.status IS '微服务状态：ACTIVE 或 DISABLED';

CREATE UNIQUE INDEX uk_service_project_code_active
    ON service (project_id, service_code)
    WHERE deleted = FALSE;

CREATE INDEX idx_service_project_status_created_at
    ON service (project_id, status, created_at DESC);

CREATE TABLE service_environment (
    id             BIGINT PRIMARY KEY,
    project_id     BIGINT    NOT NULL,
    service_id     BIGINT    NOT NULL,
    environment_id BIGINT    NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    deleted        BOOLEAN   NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_service_environment_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_service_environment_service
        FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_service_environment_environment
        FOREIGN KEY (environment_id) REFERENCES environment (id)
);

COMMENT ON TABLE service_environment IS '微服务与项目环境关联';
COMMENT ON COLUMN service_environment.project_id IS '冗余项目 ID，用于租户和归属校验';

CREATE UNIQUE INDEX uk_service_environment_active
    ON service_environment (service_id, environment_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_service_environment_project_environment
    ON service_environment (project_id, environment_id, deleted);

CREATE INDEX idx_service_environment_project_service
    ON service_environment (project_id, service_id, deleted);
