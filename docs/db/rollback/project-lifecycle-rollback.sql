-- 项目生命周期操作的人工回滚参考，不会被 Flyway 自动执行。
-- 执行前必须确认目标数据库、项目 ID 和恢复范围，并经过明确授权。

-- 恢复逻辑删除的项目：将 :project_id 替换为已经核对过的项目 ID。
UPDATE project
SET deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE id = :project_id;

-- 恢复项目状态：将 :project_id 和 :status 替换为已经核对过的值。
UPDATE project
SET status = :status,
    updated_at = CURRENT_TIMESTAMP
WHERE id = :project_id
  AND deleted = FALSE;
