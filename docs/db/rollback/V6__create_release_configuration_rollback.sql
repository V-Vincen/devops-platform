-- 仅用于人工评审和明确授权后的回滚，不会被 Flyway 自动执行。
-- 目标数据库：PostgreSQL / devops_platform。
-- 前提：V7 未执行，或已先执行 V7 的回滚脚本；确认新配置没有被业务接口或 Jenkins 使用。
-- 影响：删除 V6 为测试服务生成的仓库、构建定义和发布策略，旧 service 字段不会删除。
-- 停止条件：任一表存在正式发布配置或后续版本外键引用时停止，不得强制删除。

DROP TABLE IF EXISTS service_environment_policy;
DROP TABLE IF EXISTS service_build_spec;
DROP TABLE IF EXISTS code_repository;
