# 本地基础依赖

本目录只启动开发期基础依赖：PostgreSQL 和 Redis。

```bash
cp .env.example .env
docker compose up -d postgres redis
docker compose ps
```

Nacos 不直接写入此 Compose 文件，是因为 Nacos 镜像大版本、PostgreSQL Schema 和端口配置必须与实际锁定版本一致。部署说明见 `../k8s/nacos/README.md`。

平台后端默认开启登录认证。首次启动后端时，在命令前提供本地管理员密码：

```bash
cd ../../backend
DEVOPS_BOOTSTRAP_ADMIN_PASSWORD='replace-with-local-password' mvn spring-boot:run
```

前端登录地址为 `http://localhost:5173/login`。如果只是临时排查旧接口，可以设置
`DEVOPS_SECURITY_ENABLED=false`，排查完成后应恢复为 `true`。

如需同时初始化开发人员账号，再提供
`DEVOPS_BOOTSTRAP_DEVELOPER_USERNAME` 和 `DEVOPS_BOOTSTRAP_DEVELOPER_PASSWORD`。
