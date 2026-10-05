# Nexus 制品生命周期与恢复操作说明

> 文档版本：S1.0  
> 更新日期：2026-10-05  
> 适用范围：本项目本机 `devops-nexus`，不适用于生产环境。

## 1. 本期目标与边界

【本期必须】

- 为 Maven、Docker 仓库制定可审计的清理策略，不直接删除现有制品。
- 将 `devops-nexus-data`、独立 `nexus` 数据库和 `infra/nexus/.env` 组成同一恢复点。
- 在不影响原 Nexus 的隔离副本中验证备份可恢复。

【实施参考】

- Nexus Community 不提供按“保留最近 N 个版本”的策略能力；本期使用下载时间、预发布版本和正则条件。
- 清理任务先进行预览，再由管理员在 Nexus 管理界面创建、关联和定时执行。
- 清理任务只会软删除制品；确认恢复窗口结束后，才可另行执行 Blob Store 压缩以释放磁盘空间。

【未来扩展】

- 在测试服务器中将备份复制到独立存储。
- 用定时任务每天执行一次备份，并按保留周期清理过期备份集。
- 制品容量、清理结果和备份失败告警。

## 2. 清理策略

| 策略名称 | 适用仓库 | 条件 | 初始状态 | 风险控制 |
|---|---|---|---|---|
| `devops-maven-snapshots-30d` | `maven-snapshots` | 预发布版本且最后下载超过 30 天 | 草案，未关联仓库 | 预览结果中排除验收坐标 `com.vincent.devops.verification` |
| `devops-maven-central-cache-60d` | `maven-central` | 最后下载超过 60 天 | 草案，未关联仓库 | 仅清代理缓存；缺失时可重新从上游下载 |
| `devops-docker-proxy-cache-30d` | `docker-proxy` | 最后下载超过 30 天 | 草案，未关联仓库 | 仅清代理缓存；先确认上游镜像仍可拉取 |
| `devops-docker-hosted-review` | `docker-hosted` | 不自动执行 | 草案 | 私有镜像由部署记录决定，先人工评审后再配置 |
| `devops-maven-releases-protect` | `maven-releases` | 不自动执行 | 固定保护 | 已发布 Release 不在本期自动清理范围内 |

`maven-public` 与 `docker-group` 是聚合仓库，不直接配置清理策略。Docker 的清理策略仅针对带标签的镜像清单；无标签清单和按摘要拉取的镜像不纳入自动删除。

## 3. 在 Nexus 管理界面执行清理策略

1. 管理员进入 **Administration → Cleanup policies**，按上表创建草案策略。
2. 创建 Maven Snapshot 策略时，选择“预发布版本”和“最后下载天数”；在 Maven 路径正则中排除验收坐标，先检查预览结果。
3. 进入目标 Hosted 或 Proxy 仓库的 Cleanup 设置，只关联对应一条草案策略。
4. 使用 Cleanup policy preview 核对候选列表；预览只代表当时状态，正式执行前应再次检查。
5. 通过预览后，创建每日 `02:20` 的 Cleanup 任务；首次仅关联 `maven-central` 和 `docker-proxy`。
6. `maven-snapshots` 和 `docker-hosted` 必须由维护人审核预览后再启用；`maven-releases`、`maven-public`、`docker-group` 不启用自动清理。
7. 清理后保留至少一个恢复观察窗口；只有确认不需要恢复已软删除制品后，才可在维护窗口压缩 Blob Store。

清理策略不等于备份。任何首次启用、修改规则或 Blob Store 压缩前，都必须先有可用的最新恢复演练记录。

## 4. 一致性备份

备份脚本使用本项目固定的 Nexus 镜像归档数据卷，并使用 PostgreSQL `pg_dump` 自定义格式归档独立数据库。原 Nexus 在归档窗口内短暂停止，以使 Blob Store 与元数据保持同一时间点。

```bash
cd /Users/vincent/Projects/AiCode/devops/devops-platform
python3 infra/nexus/scripts/backup_nexus.py
```

备份文件直接写入 `~/.codex/backups/`，不写入项目目录，文件包括：

- `nexus--devops-nexus-data--<时间>-backup.tar`：Blob Store、Nexus 运行配置与数据卷内容。
- `nexus--nexus-database--<时间>-backup.dump`：PostgreSQL 自定义归档。
- `nexus--infra-nexus-.env--<时间>-backup.env`：受保护配置副本，权限为仅当前用户可读写。
- `nexus--manifest--<时间>-backup.json`：不含密码的文件清单与 SHA-256 校验和。

停止条件：数据卷归档、数据库归档、配置副本或校验和任一失败时，备份演练失败。脚本仍会尝试重新启动原 Nexus；不得将不完整文件当成有效恢复点。

## 5. 隔离恢复验证

选择同一时间戳的清单文件，执行：

```bash
python3 infra/nexus/scripts/verify_nexus_restore.py \
  --manifest ~/.codex/backups/nexus--manifest--<时间>-backup.json
```

脚本依次校验三份备份的 SHA-256、创建唯一命名的 `nexus_restore_<时间>` 数据库、还原数据卷、启动临时 Nexus，并通过端口 `28081` 的健康接口和仓库 REST API 检查数据库表与关键仓库。

验证结束后，临时 Nexus 容器会停止；隔离容器、数据卷和数据库会保留，便于复查和追溯。脚本不会删除它们。清理这些隔离资源需要单独确认，且必须保留验证记录中的资源名称。

## 6. 恢复与回滚原则

原实例发生故障时，恢复顺序固定为：

```text
停止原 Nexus
  ↓
恢复匹配的 PostgreSQL 归档
  ↓
恢复匹配的数据卷归档
  ↓
核对受保护配置的校验和与 JDBC 配置
  ↓
启动 Nexus 并核验健康状态、仓库和制品
```

不得把不同时间戳的数据库和数据卷混用。出现恢复失败、配置校验不一致、数据库表数量异常或关键仓库缺失时，停止后续操作，保留现场和日志，使用上一组已验证的备份重新演练。

## 7. 本次演练记录

| 项目 | 实际结果 |
|---|---|
| 备份集 | 2026-10-05 创建；数据卷、数据库、受保护配置和 SHA-256 清单均位于 `~/.codex/backups/` |
| 原 Nexus | 一致性备份后自动恢复为 `healthy`；未删除原数据库、原数据卷或制品 |
| 隔离数据库 | `nexus_restore_20261004200121`，结构核验为 `204` 张表 |
| 隔离容器 | `devops-nexus-restore-20261004200121`，端口 `28081` 至 `28084`；健康接口与仓库 REST API 均通过 |
| 仓库核验 | `10` 个仓库可查询，包含 Maven 与 Docker 的关键仓库 |
| 收尾状态 | 隔离容器已停止；隔离数据卷与数据库保留，未执行删除 |

结论：当前备份集可用于恢复同一时间点的 Nexus 数据卷、元数据和受保护配置。隔离副本的删除不属于本次演练范围。

### 术语注解

**Blob Store**¹：Nexus 保存 Maven 制品、Docker 镜像等二进制内容的数据存储。  
**恢复点**²：能同时还原数据库元数据、二进制数据和运行配置的一组匹配备份。  
**软删除**³：先标记为删除、仍可能恢复的删除状态，尚未立即释放物理磁盘空间。  
**SHA-256**⁴：用于验证备份文件未损坏、未被替换的校验值。
