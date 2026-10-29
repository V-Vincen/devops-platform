-- 目标数据库：PostgreSQL / devops_platform。
-- 业务目的：记录构建、镜像与部署的不可变执行历史，支持查询、失败定位和按镜像 Digest 回滚。
-- 前提：V6 已成功创建代码仓库、构建定义和发布策略。
-- 历史数据：不回填历史执行记录；原有测试数据没有可验证的构建、镜像和部署事实。
-- 重复执行：Flyway 仅执行一次；失败会在同一事务中回滚。索引创建可能短暂获取表锁，锁等待最多 5 秒。
-- 回滚：仅限明确授权后执行 docs/db/rollback/V7__create_release_execution_records_rollback.sql。
SET lock_timeout = '5s';

CREATE TABLE pipeline_run (
    id                    BIGINT       PRIMARY KEY,
    project_id            BIGINT       NOT NULL,
    service_id            BIGINT       NOT NULL,
    environment_id        BIGINT       NOT NULL,
    repository_id         BIGINT       NOT NULL,
    build_spec_id         BIGINT       NOT NULL,
    trigger_type          VARCHAR(20)  NOT NULL,
    external_event_id     VARCHAR(255),
    git_ref               VARCHAR(255) NOT NULL,
    commit_sha            VARCHAR(128),
    status                VARCHAR(20)  NOT NULL,
    jenkins_job_name      VARCHAR(255),
    jenkins_build_number  BIGINT,
    jenkins_build_url     VARCHAR(1000),
    parameter_snapshot    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    queued_at             TIMESTAMP    NOT NULL,
    started_at            TIMESTAMP,
    finished_at           TIMESTAMP,
    failure_summary       VARCHAR(2000),
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    CONSTRAINT fk_pipeline_run_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_pipeline_run_service
        FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_pipeline_run_environment
        FOREIGN KEY (environment_id) REFERENCES environment (id),
    CONSTRAINT fk_pipeline_run_repository
        FOREIGN KEY (repository_id) REFERENCES code_repository (id),
    CONSTRAINT fk_pipeline_run_build_spec
        FOREIGN KEY (build_spec_id) REFERENCES service_build_spec (id),
    CONSTRAINT ck_pipeline_run_trigger_type
        CHECK (trigger_type IN ('MANUAL', 'WEBHOOK', 'RETRY')),
    CONSTRAINT ck_pipeline_run_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELED')),
    CONSTRAINT ck_pipeline_run_parameter_snapshot
        CHECK (jsonb_typeof(parameter_snapshot) = 'object')
);

COMMENT ON TABLE pipeline_run IS '一次构建和部署请求的执行记录，保留触发来源和 Jenkins 执行状态';
COMMENT ON COLUMN pipeline_run.id IS '构建记录 ID，由应用生成';
COMMENT ON COLUMN pipeline_run.project_id IS '所属项目 ID，用于隔离查询';
COMMENT ON COLUMN pipeline_run.service_id IS '目标微服务 ID';
COMMENT ON COLUMN pipeline_run.environment_id IS '目标环境 ID';
COMMENT ON COLUMN pipeline_run.repository_id IS '构建所用代码仓库 ID';
COMMENT ON COLUMN pipeline_run.build_spec_id IS '构建所用构建定义 ID';
COMMENT ON COLUMN pipeline_run.trigger_type IS '触发方式：MANUAL、WEBHOOK 或 RETRY';
COMMENT ON COLUMN pipeline_run.external_event_id IS '外部代码平台事件标识，用于 Webhook 去重';
COMMENT ON COLUMN pipeline_run.git_ref IS '本次构建的 Git 分支或标签';
COMMENT ON COLUMN pipeline_run.commit_sha IS '本次构建的提交标识';
COMMENT ON COLUMN pipeline_run.status IS '执行状态：待执行、执行中、成功、失败或取消';
COMMENT ON COLUMN pipeline_run.jenkins_job_name IS '执行 Jenkins Job 名称';
COMMENT ON COLUMN pipeline_run.jenkins_build_number IS 'Jenkins 构建编号';
COMMENT ON COLUMN pipeline_run.jenkins_build_url IS 'Jenkins 构建日志链接';
COMMENT ON COLUMN pipeline_run.parameter_snapshot IS '不含密钥的执行参数快照';
COMMENT ON COLUMN pipeline_run.queued_at IS '进入构建队列时间';
COMMENT ON COLUMN pipeline_run.started_at IS '实际开始时间';
COMMENT ON COLUMN pipeline_run.finished_at IS '结束时间';
COMMENT ON COLUMN pipeline_run.failure_summary IS '面向用户的失败摘要，不保存凭据或完整堆栈';
COMMENT ON COLUMN pipeline_run.created_at IS '创建时间';
COMMENT ON COLUMN pipeline_run.updated_at IS '更新时间';

CREATE UNIQUE INDEX uk_pipeline_run_repository_external_event
    ON pipeline_run (repository_id, external_event_id)
    WHERE external_event_id IS NOT NULL;

CREATE INDEX idx_pipeline_run_project_service_environment_created_at
    ON pipeline_run (project_id, service_id, environment_id, created_at DESC);

CREATE INDEX idx_pipeline_run_status_queued_at
    ON pipeline_run (status, queued_at);

CREATE TABLE image_artifact (
    id                  BIGINT       PRIMARY KEY,
    project_id          BIGINT       NOT NULL,
    service_id          BIGINT       NOT NULL,
    pipeline_run_id     BIGINT       NOT NULL,
    image_repository    VARCHAR(500) NOT NULL,
    image_tag           VARCHAR(255) NOT NULL,
    image_digest        VARCHAR(255) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    published_at        TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT fk_image_artifact_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_image_artifact_service
        FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_image_artifact_pipeline_run
        FOREIGN KEY (pipeline_run_id) REFERENCES pipeline_run (id),
    CONSTRAINT ck_image_artifact_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_image_artifact_digest
        CHECK (image_digest LIKE 'sha256:%')
);

COMMENT ON TABLE image_artifact IS '构建成功后推送至镜像仓库的不可变镜像记录';
COMMENT ON COLUMN image_artifact.id IS '镜像制品记录 ID，由应用生成';
COMMENT ON COLUMN image_artifact.project_id IS '所属项目 ID，用于隔离查询';
COMMENT ON COLUMN image_artifact.service_id IS '所属微服务 ID';
COMMENT ON COLUMN image_artifact.pipeline_run_id IS '产出该镜像的构建记录 ID';
COMMENT ON COLUMN image_artifact.image_repository IS '镜像仓库地址，不含标签';
COMMENT ON COLUMN image_artifact.image_tag IS '镜像标签，仅用于展示和检索';
COMMENT ON COLUMN image_artifact.image_digest IS '不可变镜像 Digest，用于准确部署和回滚';
COMMENT ON COLUMN image_artifact.status IS '镜像状态：PENDING、PUBLISHED 或 FAILED';
COMMENT ON COLUMN image_artifact.published_at IS '镜像推送成功时间';
COMMENT ON COLUMN image_artifact.created_at IS '创建时间';
COMMENT ON COLUMN image_artifact.updated_at IS '更新时间';

CREATE UNIQUE INDEX uk_image_artifact_repository_digest
    ON image_artifact (image_repository, image_digest);

CREATE INDEX idx_image_artifact_project_service_published_at
    ON image_artifact (project_id, service_id, published_at DESC);

CREATE TABLE deployment_record (
    id                       BIGINT       PRIMARY KEY,
    project_id               BIGINT       NOT NULL,
    service_id               BIGINT       NOT NULL,
    environment_id           BIGINT       NOT NULL,
    pipeline_run_id          BIGINT       NOT NULL,
    image_artifact_id        BIGINT       NOT NULL,
    previous_deployment_id   BIGINT,
    operation_type           VARCHAR(20)  NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    helm_release_name        VARCHAR(128),
    helm_revision            INTEGER,
    deployed_at              TIMESTAMP,
    finished_at              TIMESTAMP,
    failure_summary          VARCHAR(2000),
    created_at               TIMESTAMP    NOT NULL,
    updated_at               TIMESTAMP    NOT NULL,
    CONSTRAINT fk_deployment_record_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_deployment_record_service
        FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_deployment_record_environment
        FOREIGN KEY (environment_id) REFERENCES environment (id),
    CONSTRAINT fk_deployment_record_pipeline_run
        FOREIGN KEY (pipeline_run_id) REFERENCES pipeline_run (id),
    CONSTRAINT fk_deployment_record_image_artifact
        FOREIGN KEY (image_artifact_id) REFERENCES image_artifact (id),
    CONSTRAINT fk_deployment_record_previous
        FOREIGN KEY (previous_deployment_id) REFERENCES deployment_record (id),
    CONSTRAINT ck_deployment_record_operation_type
        CHECK (operation_type IN ('DEPLOY', 'ROLLBACK')),
    CONSTRAINT ck_deployment_record_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELED'))
);

COMMENT ON TABLE deployment_record IS '镜像部署或回滚至指定环境的执行记录';
COMMENT ON COLUMN deployment_record.id IS '部署记录 ID，由应用生成';
COMMENT ON COLUMN deployment_record.project_id IS '所属项目 ID，用于隔离查询';
COMMENT ON COLUMN deployment_record.service_id IS '目标微服务 ID';
COMMENT ON COLUMN deployment_record.environment_id IS '目标环境 ID';
COMMENT ON COLUMN deployment_record.pipeline_run_id IS '关联构建记录 ID';
COMMENT ON COLUMN deployment_record.image_artifact_id IS '实际部署的不可变镜像记录 ID';
COMMENT ON COLUMN deployment_record.previous_deployment_id IS '回滚时所依据的上一次成功部署记录 ID';
COMMENT ON COLUMN deployment_record.operation_type IS '操作类型：DEPLOY 或 ROLLBACK';
COMMENT ON COLUMN deployment_record.status IS '部署状态：待执行、执行中、成功、失败或取消';
COMMENT ON COLUMN deployment_record.helm_release_name IS 'Helm Release 名称';
COMMENT ON COLUMN deployment_record.helm_revision IS 'Helm Revision 编号';
COMMENT ON COLUMN deployment_record.deployed_at IS '开始部署时间';
COMMENT ON COLUMN deployment_record.finished_at IS '部署结束时间';
COMMENT ON COLUMN deployment_record.failure_summary IS '面向用户的失败摘要，不保存凭据或完整堆栈';
COMMENT ON COLUMN deployment_record.created_at IS '创建时间';
COMMENT ON COLUMN deployment_record.updated_at IS '更新时间';

CREATE INDEX idx_deployment_record_project_service_environment_created_at
    ON deployment_record (project_id, service_id, environment_id, created_at DESC);

CREATE INDEX idx_deployment_record_status_created_at
    ON deployment_record (status, created_at DESC);
