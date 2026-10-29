-- 仅用于人工评审和明确授权后的回滚，不会被 Flyway 自动执行。
-- 目标数据库：PostgreSQL / devops_platform。
-- 前提：确认 pipeline_run、image_artifact、deployment_record 均未保存需要保留的发布审计记录。
-- 影响：删除所有构建、镜像和部署历史；已推送的 Nexus 镜像和已部署的 K3s 工作负载不会自动回滚。
-- 停止条件：如存在成功部署、生产环境记录或审计要求，停止执行并先完成备份与人工恢复方案。

DROP TABLE IF EXISTS deployment_record;
DROP TABLE IF EXISTS image_artifact;
DROP TABLE IF EXISTS pipeline_run;
