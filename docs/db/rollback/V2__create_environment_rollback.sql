-- 仅用于人工评审和明确授权后的回滚，不会被 Flyway 自动执行。
-- 执行前必须确认目标数据库和 environment 表中没有需要保留的数据。
DROP TABLE IF EXISTS environment;

-- 如只需要恢复某个环境的逻辑删除或状态，请使用以下参考语句：
-- UPDATE environment
-- SET deleted = FALSE, updated_at = CURRENT_TIMESTAMP
-- WHERE id = :environment_id;
--
-- UPDATE environment
-- SET status = :status, updated_at = CURRENT_TIMESTAMP
-- WHERE id = :environment_id AND deleted = FALSE;
