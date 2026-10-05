# Redis 部署说明

Redis 作为平台基础设施部署在 K3s 的 `platform-infra` Namespace。

## 测试环境

- 单实例 StatefulSet。
- 使用 PVC 保存数据。
- 不启用 Sentinel 或 Redis Cluster。
- 启用密码和 ACL。
- 默认启用 AOF everysec，并保留 RDB 快照。

## Key 隔离

```text
devops:*
project-a:dev:*
project-a:test:*
```

平台和业务项目使用不同 Redis 账号。平台第一期可以不读取 Redis，业务微服务按需接入。
