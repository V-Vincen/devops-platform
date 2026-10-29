-- 目标数据库：PostgreSQL / devops_platform。
-- 业务目的：将代码仓库、服务构建定义和环境发布策略从 service 的基础信息中拆分，
-- 为独立仓库与单仓库多模块发布提供统一配置。
-- 历史数据：现有 service 和 service_environment 记录仅为测试数据；本迁移为每个未删除服务创建默认配置。
-- 兼容性：保留 service 的 repository_url、branch_name、build_type、image_repository，旧接口可继续使用。
-- 重复执行：Flyway 仅执行一次；失败会在同一事务中回滚。索引创建可能短暂获取表锁，锁等待最多 5 秒。
-- 回滚：仅限明确授权后执行 docs/db/rollback/V6__create_release_configuration_rollback.sql。
SET lock_timeout = '5s';

CREATE TABLE code_repository (
    id                     BIGINT       PRIMARY KEY,
    project_id             BIGINT       NOT NULL,
    repository_key         VARCHAR(128) NOT NULL,
    provider               VARCHAR(20)  NOT NULL,
    clone_url              VARCHAR(500) NOT NULL,
    default_branch         VARCHAR(128) NOT NULL,
    credential_reference   VARCHAR(128),
    webhook_secret_reference VARCHAR(128),
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    description            VARCHAR(500),
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP    NOT NULL,
    deleted                BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_code_repository_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT ck_code_repository_provider
        CHECK (provider IN ('GITHUB', 'GITEE')),
    CONSTRAINT ck_code_repository_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE code_repository IS '项目代码仓库配置，不保存访问令牌或 Webhook 密钥明文';
COMMENT ON COLUMN code_repository.id IS '代码仓库 ID，由应用生成';
COMMENT ON COLUMN code_repository.project_id IS '所属项目 ID';
COMMENT ON COLUMN code_repository.repository_key IS '项目内唯一的仓库标识';
COMMENT ON COLUMN code_repository.provider IS '代码平台：GITHUB 或 GITEE';
COMMENT ON COLUMN code_repository.clone_url IS 'Git 克隆地址';
COMMENT ON COLUMN code_repository.default_branch IS '默认构建分支';
COMMENT ON COLUMN code_repository.credential_reference IS '受保护凭据引用，不保存 Token 或私钥明文';
COMMENT ON COLUMN code_repository.webhook_secret_reference IS 'Webhook 密钥的受保护凭据引用';
COMMENT ON COLUMN code_repository.status IS '仓库状态：ACTIVE 或 DISABLED';
COMMENT ON COLUMN code_repository.description IS '仓库说明';
COMMENT ON COLUMN code_repository.created_at IS '创建时间';
COMMENT ON COLUMN code_repository.updated_at IS '更新时间';
COMMENT ON COLUMN code_repository.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX uk_code_repository_project_key_active
    ON code_repository (project_id, repository_key)
    WHERE deleted = FALSE;

CREATE INDEX idx_code_repository_project_status_created_at
    ON code_repository (project_id, status, created_at DESC)
    WHERE deleted = FALSE;

CREATE INDEX idx_code_repository_project_clone_url
    ON code_repository (project_id, clone_url)
    WHERE deleted = FALSE;

CREATE TABLE service_build_spec (
    id                   BIGINT       PRIMARY KEY,
    project_id           BIGINT       NOT NULL,
    service_id           BIGINT       NOT NULL,
    repository_id        BIGINT       NOT NULL,
    source_root          VARCHAR(500) NOT NULL DEFAULT '/',
    change_path_rules    JSONB        NOT NULL DEFAULT '["/**"]'::jsonb,
    build_workdir        VARCHAR(500) NOT NULL DEFAULT '.',
    build_type           VARCHAR(32)  NOT NULL,
    build_command        VARCHAR(1000),
    dockerfile_path      VARCHAR(500) NOT NULL DEFAULT 'Dockerfile',
    docker_context_path  VARCHAR(500) NOT NULL DEFAULT '.',
    image_repository     VARCHAR(500) NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL,
    deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_service_build_spec_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_service_build_spec_service
        FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_service_build_spec_repository
        FOREIGN KEY (repository_id) REFERENCES code_repository (id),
    CONSTRAINT ck_service_build_spec_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_service_build_spec_change_path_rules
        CHECK (jsonb_typeof(change_path_rules) = 'array')
);

COMMENT ON TABLE service_build_spec IS '服务构建定义，支持独立仓库和单仓库多模块';
COMMENT ON COLUMN service_build_spec.id IS '构建定义 ID，由应用生成';
COMMENT ON COLUMN service_build_spec.project_id IS '所属项目 ID，用于归属校验';
COMMENT ON COLUMN service_build_spec.service_id IS '关联微服务 ID';
COMMENT ON COLUMN service_build_spec.repository_id IS '关联代码仓库 ID';
COMMENT ON COLUMN service_build_spec.source_root IS '服务在仓库中的源码根目录';
COMMENT ON COLUMN service_build_spec.change_path_rules IS '用于识别服务变更的仓库相对路径规则数组';
COMMENT ON COLUMN service_build_spec.build_workdir IS '执行构建命令的工作目录';
COMMENT ON COLUMN service_build_spec.build_type IS '构建类型，例如 MAVEN 或 NODE_JS';
COMMENT ON COLUMN service_build_spec.build_command IS '可选的受控构建命令模板';
COMMENT ON COLUMN service_build_spec.dockerfile_path IS '相对源码根目录的 Dockerfile 路径';
COMMENT ON COLUMN service_build_spec.docker_context_path IS 'Docker 构建上下文目录';
COMMENT ON COLUMN service_build_spec.image_repository IS '目标 Docker 镜像仓库地址，不含标签';
COMMENT ON COLUMN service_build_spec.status IS '构建定义状态：ACTIVE 或 DISABLED';
COMMENT ON COLUMN service_build_spec.created_at IS '创建时间';
COMMENT ON COLUMN service_build_spec.updated_at IS '更新时间';
COMMENT ON COLUMN service_build_spec.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX uk_service_build_spec_service_active
    ON service_build_spec (service_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_service_build_spec_project_repository
    ON service_build_spec (project_id, repository_id)
    WHERE deleted = FALSE;

CREATE TABLE service_environment_policy (
    id                     BIGINT       PRIMARY KEY,
    project_id             BIGINT       NOT NULL,
    service_environment_id BIGINT       NOT NULL,
    service_id             BIGINT       NOT NULL,
    environment_id         BIGINT       NOT NULL,
    release_policy         VARCHAR(32)  NOT NULL,
    approval_required      BOOLEAN      NOT NULL DEFAULT TRUE,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP    NOT NULL,
    deleted                BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_service_environment_policy_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_service_environment_policy_relation
        FOREIGN KEY (service_environment_id) REFERENCES service_environment (id),
    CONSTRAINT fk_service_environment_policy_service
        FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_service_environment_policy_environment
        FOREIGN KEY (environment_id) REFERENCES environment (id),
    CONSTRAINT ck_service_environment_policy_release_policy
        CHECK (release_policy IN ('MANUAL_ONLY', 'AUTO_BUILD_MANUAL_DEPLOY', 'AUTO_BUILD_AUTO_DEPLOY')),
    CONSTRAINT ck_service_environment_policy_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE service_environment_policy IS '服务在指定环境中的构建和发布策略';
COMMENT ON COLUMN service_environment_policy.id IS '发布策略 ID，由应用生成';
COMMENT ON COLUMN service_environment_policy.project_id IS '所属项目 ID，用于归属校验';
COMMENT ON COLUMN service_environment_policy.service_environment_id IS '服务与环境关联 ID';
COMMENT ON COLUMN service_environment_policy.service_id IS '关联微服务 ID，便于项目隔离查询';
COMMENT ON COLUMN service_environment_policy.environment_id IS '关联环境 ID，便于项目隔离查询';
COMMENT ON COLUMN service_environment_policy.release_policy IS '发布模式：仅手动、自动构建后手动部署、自动构建并自动部署';
COMMENT ON COLUMN service_environment_policy.approval_required IS '部署前是否必须人工确认';
COMMENT ON COLUMN service_environment_policy.status IS '策略状态：ACTIVE 或 DISABLED';
COMMENT ON COLUMN service_environment_policy.created_at IS '创建时间';
COMMENT ON COLUMN service_environment_policy.updated_at IS '更新时间';
COMMENT ON COLUMN service_environment_policy.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX uk_service_environment_policy_relation_active
    ON service_environment_policy (service_environment_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_service_environment_policy_project_environment
    ON service_environment_policy (project_id, environment_id, status)
    WHERE deleted = FALSE;

-- 历史服务均为测试数据。保留旧列以兼容现有接口，并生成安全的默认发布配置。
INSERT INTO code_repository (
    id, project_id, repository_key, provider, clone_url, default_branch,
    status, created_at, updated_at, deleted
)
SELECT
    s.id, s.project_id, s.service_code, 'GITHUB', s.repository_url, s.branch_name,
    CASE WHEN s.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'DISABLED' END,
    s.created_at, s.updated_at, s.deleted
FROM service s
WHERE s.deleted = FALSE;

INSERT INTO service_build_spec (
    id, project_id, service_id, repository_id, source_root, change_path_rules,
    build_workdir, build_type, dockerfile_path, docker_context_path, image_repository,
    status, created_at, updated_at, deleted
)
SELECT
    s.id, s.project_id, s.id, s.id, '/', '["/**"]'::jsonb,
    '.', s.build_type, 'Dockerfile', '.', s.image_repository,
    CASE WHEN s.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'DISABLED' END,
    s.created_at, s.updated_at, s.deleted
FROM service s
WHERE s.deleted = FALSE;

INSERT INTO service_environment_policy (
    id, project_id, service_environment_id, service_id, environment_id,
    release_policy, approval_required, status, created_at, updated_at, deleted
)
SELECT
    se.id, se.project_id, se.id, se.service_id, se.environment_id,
    'MANUAL_ONLY', TRUE,
    CASE WHEN s.status = 'ACTIVE' AND e.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'DISABLED' END,
    se.created_at, se.updated_at, se.deleted
FROM service_environment se
JOIN service s ON s.id = se.service_id
JOIN environment e ON e.id = se.environment_id
WHERE se.deleted = FALSE;
