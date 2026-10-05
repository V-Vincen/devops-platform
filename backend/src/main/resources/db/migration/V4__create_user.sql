CREATE TABLE sys_user (
    id            BIGINT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_sys_user_role
        CHECK (role IN ('ADMIN', 'DEVELOPER')),
    CONSTRAINT ck_sys_user_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE sys_user IS 'DevOps 平台用户';
COMMENT ON COLUMN sys_user.username IS '登录用户名';
COMMENT ON COLUMN sys_user.password_hash IS 'BCrypt 密码摘要，不保存明文密码';
COMMENT ON COLUMN sys_user.role IS '用户角色：ADMIN 或 DEVELOPER';
COMMENT ON COLUMN sys_user.status IS '用户状态：ACTIVE 或 DISABLED';

CREATE UNIQUE INDEX uk_sys_user_username_active
    ON sys_user (username)
    WHERE deleted = FALSE;

CREATE INDEX idx_sys_user_status_role
    ON sys_user (status, role);
