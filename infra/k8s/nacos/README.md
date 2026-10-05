# Nacos 部署说明

Nacos 作为微服务的注册中心和配置中心，部署在 K3s 的 `platform-infra` Namespace。

## 约束

- 测试环境使用单实例。
- 使用外部 PostgreSQL 数据库 `nacos`。
- 必须挂载 PVC，不能使用临时 emptyDir 作为正式测试数据目录。
- 必须启用 Nacos 认证。
- 只允许内网访问，不暴露公网。
- 按实际锁定的 Nacos 大版本核对 8848、9848、9849 和控制台端口。

## 数据库准备

1. 创建独立数据库 `nacos` 和账号 `nacos_user`。
2. 从对应 Nacos 版本的发行包中获取 PostgreSQL Schema。
3. 在目标数据库执行官方 Schema。
4. 使用 Secret 注入数据库密码和 Nacos 鉴权密钥。

## 应用接入

```text
NACOS_SERVER_ADDR=nacos.platform-infra.svc.cluster.local:8848
NACOS_NAMESPACE=dev 或 test
NACOS_GROUP=项目编码
```

Nacos 客户端还会使用 gRPC 端口，不能只放行 HTTP API 端口。
