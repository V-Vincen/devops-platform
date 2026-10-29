# Nexus 制品生命周期与恢复操作说明

> 文档版本：S1.2
> 更新日期：2026-10-21
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

上表的初始状态记录策略启用前的状态。根据已完成的配置核验，前三项策略已分别关联对应仓库；`docker-hosted` 和 `maven-releases` 保持不自动清理。Cleanup service 已设置为每天运行，具体时间以 Nexus 界面显示的 `Next run` 为准。此处是已有核验记录，实际状态应以 Nexus 当前配置为准。

`maven-public` 与 `docker-group` 是聚合仓库，不直接配置清理策略。Docker 的清理策略仅针对带标签的镜像清单；无标签清单和按摘要拉取的镜像不纳入自动删除。

### 2.1 策略字段清单

| 策略名称 | Format | Release Type | Cleanup Criteria | Asset Name Matcher | 创建后的状态 |
|---|---|---|---|---|---|
| `devops-maven-snapshots-30d` | `maven2` | `Pre-Releases/Snapshots` | 仅勾选 `Component Usage`，填 `30` | `^(?!.*?/com/vincent/devops/verification/).*$` | 保存为策略；通过复核前不关联 `maven-snapshots` |
| `devops-maven-central-cache-60d` | `maven2` | 不选择 | 仅勾选 `Component Usage`，填 `60` | 不选择 | 预览通过后关联 `maven-central` |
| `devops-docker-proxy-cache-30d` | `docker` | 不选择 | 仅勾选 `Component Usage`，填 `30` | 不选择 | 预览和上游拉取验证通过后关联 `docker-proxy` |
| `devops-docker-hosted-review` | 不创建自动清理策略 | 不适用 | 不适用 | 不适用 | 保持未关联，按部署记录人工评审 |
| `devops-maven-releases-protect` | 不创建自动清理策略 | 不适用 | 不适用 | 不适用 | 保持未关联，保护已发布 Release |

`Component Age` 不用于本期策略。勾选多个 Cleanup Criteria 时，候选制品必须同时满足全部已勾选条件；为避免把“30 天前发布、近期仍在使用”的制品误纳入或误排除，本期只按最后下载时间筛选。

本项目 Nexus 使用 PostgreSQL。`Asset Name Matcher` 使用 Java 正则表达式，路径以 `/` 开头；Snapshot 策略中的负向匹配用于排除验收坐标，而不是选择仅清理该坐标。

## 3. 在 Nexus 管理界面配置清理策略

### 3.1 通用创建与预览流程

1. 管理员进入 **Settings → Repository → Cleanup Policies**，点击创建策略。
2. 填写唯一策略名称、Format 和说明；名称使用本章表格中的固定值。
3. 仅勾选表格指定的 Cleanup Criteria，不增加 `Component Age` 或未说明的匹配条件。
4. 在 **Preview Cleanup Policy Results** 中选择目标仓库，生成 CSV 报告。CSV 包含 namespace、name、version 和 path，只代表生成时刻的候选集。
5. 核对 CSV 后保存策略。保存策略本身不会删除制品；只有策略被关联到仓库且 Cleanup service 执行时才会生效。
6. 在关联策略和首次计划执行之间，必须重新生成一次预览。新发布或新下载的制品可能改变候选集。

### 3.2 `devops-maven-snapshots-30d`

1. 创建策略，名称填 `devops-maven-snapshots-30d`，Format 选 `maven2`；说明填写“清理 30 天未下载的 Maven Snapshot，排除验收制品”。
2. Release Type 选 `Pre-Releases/Snapshots`。
3. 勾选 `Component Usage`，填 `30`；保持 `Component Age` 不勾选。
4. 勾选 `Asset Name Matcher`，填入：

   ```text
   ^(?!.*?/com/vincent/devops/verification/).*$
   ```

5. 在预览中选择 `maven-snapshots`，生成 CSV。确认候选项均为超过 30 天未下载的 Snapshot，且 CSV 中不存在 `com.vincent.devops.verification`。
6. 保存策略后，不立即关联仓库，不点击 Cleanup service 的 **Run**。将 CSV 作为复核记录。
7. 维护人确认最新 CSV 后，进入 **Settings → Repository → Repositories → maven-snapshots → Cleanup**，将该策略移入已应用策略列表并保存。关联后等待计划任务执行，不手动运行。

### 3.3 `devops-maven-central-cache-60d`

1. 创建策略，名称填 `devops-maven-central-cache-60d`，Format 选 `maven2`；说明填写“清理 60 天未下载的 Maven Central 代理缓存”。
2. 不选择 Release Type，不勾选 `Component Age` 或 `Asset Name Matcher`；仅勾选 `Component Usage` 并填 `60`。
3. 在预览中选择 `maven-central`，检查候选项均为代理缓存，不包含本项目 Hosted 制品。
4. 从常用坐标或项目依赖中任选一个候选制品，确认其上游仍可下载；代理缓存被清理后应能按需重新获取。
5. 保存策略后，进入 `maven-central` 的 **Cleanup** 设置，关联该策略并保存。

### 3.4 `devops-docker-proxy-cache-30d`

1. 创建策略，名称填 `devops-docker-proxy-cache-30d`，Format 选 `docker`；说明填写“清理 30 天未下载的 Docker 代理缓存”。
2. 不选择 Release Type，不勾选 `Component Age` 或 `Asset Name Matcher`；仅勾选 `Component Usage` 并填 `30`。
3. 在预览中选择 `docker-proxy`，检查候选项均为代理缓存，并记录至少一个候选镜像名称和标签。
4. 从上游验证该镜像标签仍可拉取，避免因上游镜像已删除而失去可重建的缓存。
5. 保存策略后，进入 `docker-proxy` 的 **Cleanup** 设置，关联该策略并保存。

### 3.5 不自动清理的仓库

- `docker-hosted`：不创建或关联自动清理策略。需要清理时，先根据部署记录、镜像标签、回滚需求和维护人审批清单逐项评审，再单独执行人工操作。
- `maven-releases`：不创建或关联自动清理策略。已发布 Release 是回滚与审计依据。
- `maven-public` 和 `docker-group`：它们是聚合仓库，不关联清理策略；清理应配置在实际 Hosted 或 Proxy 仓库。

## 4. 设置 Cleanup service 定时任务

1. 进入 **Settings → System → Tasks**，打开现有的 **Cleanup service (Admin - Cleanup repositories using their associated policies)**。该任务已存在时不得重复创建。
2. 在 **Settings** 页保持 `Task enabled` 勾选、任务名称 `Cleanup service` 和通知条件 `Failure` 不变。未配置 Email Server 时，通知邮箱保持为空。
3. 在 `Task frequency` 中选择 `Advanced (provide a CRON expression)`。
4. 将 `CRON expression` 输入框中的旧值完整替换为：

   ```text
   0 20 2 * * ?
   ```

   当前实例保存该表达式后，界面显示的下次运行时间为中国标准时间 `02:20`。调度时区可能与容器系统时区不同，必须以保存后的 `Next run` 实际显示为准。
5. 向页面底部滚动，点击 **Save** 或 **Save task**。返回 **Summary**，确认 `Next run` 显示中国标准时间 `02:20`。
6. 不点击页面顶部的 **Run**。它会立即对所有已关联策略的仓库执行清理；首次执行应等待计划时间。

首次计划执行前，只有已经完成预览、复核和关联的仓库会成为清理对象。若尚未复核 `maven-snapshots`，它必须保持未关联；`maven-central` 和 `docker-proxy` 可在各自预览和上游验证通过后先启用。

## 5. 首次清理后的核验与恢复窗口

1. 任务结束后打开 Cleanup service 的 **Summary**，确认 `Last result` 为 `OK`。该状态仅表示任务正常结束，不代表候选范围一定正确。
2. 分别检查已启用仓库的候选制品：`maven-central` 的代理缓存应可重新下载，`docker-proxy` 的记录镜像标签应可从上游重新拉取。
3. 对已启用 Snapshot 策略的仓库，确认 `com.vincent.devops.verification:nexus-verification` 仍可查询和下载。
4. 保存首次执行前的 CSV、任务执行时间、任务结果和抽查结果，作为恢复观察记录。
5. 保留至少一个恢复观察窗口。确认不需要恢复软删除制品后，才可在单独维护窗口执行 Blob Store 压缩；压缩前必须具备最新可恢复演练记录。

清理策略不等于备份。任何首次启用、修改规则或 Blob Store 压缩前，都必须先有可用的最新恢复演练记录。

## 6. 一致性备份

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

自定义加密密钥位于数据卷的 `/nexus-data/etc/devops-nexus-secrets.json`，包含在数据卷归档中；归档和密钥均属于敏感信息，禁止提交 Git 或公开分享。恢复时必须使用同一备份集的数据库、数据卷与密钥，不得混用切换密钥前后的备份。

恢复首次配置密钥之前的旧备份时，旧数据卷不含密钥文件；原实例还需恢复切换前的 Compose 配置，再启动 Nexus。隔离恢复脚本会根据归档中是否存在密钥文件选择相应启动参数。

### 6.1 加密密钥维护

`docker-compose.yml` 通过 `NEXUS_SECRETS_KEY_FILE` 指向上述密钥文件。文件仅由 Nexus 运行用户读取，权限为 `600`。备份归档应受限保存并另存安全副本；密钥丢失可能导致已有敏感配置无法解密。

变更或轮换前，先完成一致性备份并验证恢复，保留当前密钥。根据当前版本的 Sonatype 官方重新加密流程，准备新的密钥文件并让 Nexus 读取，提交敏感配置重新加密任务，等待任务完成后再设置持久化的活动密钥。若修改 `fixedEncryption` 或加密算法，还需按官方说明处理 API 密钥的重新加密。重启后检查密钥状态、登录和仓库，再创建包含新密钥的完整备份；不要覆盖旧恢复点。失败时保留原密钥和日志，按匹配的备份集恢复，不要直接删除或替换密钥。

本次仅配置了自定义敏感配置密钥，没有调整 `fixedEncryption` 或加密算法；密钥值不记录在文档、日志或版本库中。

停止条件：数据卷归档、数据库归档、配置副本或校验和任一失败时，备份演练失败。脚本仍会尝试重新启动原 Nexus；不得将不完整文件当成有效恢复点。

## 7. 隔离恢复验证

选择同一时间戳的清单文件，执行：

```bash
python3 infra/nexus/scripts/verify_nexus_restore.py \
  --manifest ~/.codex/backups/nexus--manifest--<时间>-backup.json
```

脚本依次校验三份备份的 SHA-256、创建唯一命名的 `nexus_restore_<时间>` 数据库、还原数据卷、启动临时 Nexus，并通过端口 `28081` 的健康接口和仓库 REST API 检查数据库表与关键仓库。

验证结束后，临时 Nexus 容器会停止；隔离容器、数据卷和数据库会保留，便于复查和追溯。脚本不会删除它们。清理这些隔离资源需要单独确认，且必须保留验证记录中的资源名称。

## 8. 恢复与回滚原则

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

## 9. 演练记录

### 9.1 2026-10-05 恢复演练

| 项目 | 实际结果 |
|---|---|
| 备份集 | 2026-10-05 创建；数据卷、数据库、受保护配置和 SHA-256 清单均位于 `~/.codex/backups/` |
| 原 Nexus | 一致性备份后自动恢复为 `healthy`；未删除原数据库、原数据卷或制品 |
| 隔离数据库 | `nexus_restore_20261004200121`，结构核验为 `204` 张表 |
| 隔离容器 | `devops-nexus-restore-20261004200121`，端口 `28081` 至 `28084`；健康接口与仓库 REST API 均通过 |
| 仓库核验 | `10` 个仓库可查询，包含 Maven 与 Docker 的关键仓库 |
| 收尾状态 | 隔离容器已停止；隔离数据卷与数据库保留，未执行删除 |

结论：该备份集的隔离恢复通过。隔离副本的删除不属于本次演练范围。

### 9.2 2026-10-21 密钥配置后的恢复演练

| 项目 | 实际结果 |
|---|---|
| 备份集 | `nexus--manifest--20261020-162837Z-backup.json`，时间戳为 UTC；数据卷归档包含自定义密钥文件 |
| 验证时间 | 2026-10-21 00:37:20，中国标准时间 |
| 隔离数据库 | `nexus_restore_20261020163653`，结构核验为 `204` 张表 |
| 隔离容器 | `devops-nexus-restore-20261020163653`，验证后已停止 |
| 隔离数据卷 | `devops-nexus-restore-20261020163653`，验证后保留 |
| 服务与仓库 | 隔离 Nexus 健康接口通过；`10` 个仓库可查询；原 Nexus 保持运行 |
| 验证记录 | `~/.codex/backups/nexus--restore-check--20261020163653-record.json` |

本次验证确认包含密钥的数据卷可用于启动隔离实例；未逐项验证所有加密凭据，也未测试每个制品的下载。隔离容器、数据卷及数据库均已保留，删除前须另行确认。

### 9.3 2026-10-21 空数据卷启动验证

使用独立的临时 PostgreSQL 容器、数据库和空数据卷，以及 Nexus `3.96.3-alpine` 容器进行验证。首次启动和重启后，Nexus 健康接口均返回 HTTP `200`；启动后指定的 `/nexus-data/etc/devops-nexus-secrets.json` 文件不存在。健康检查通过不能证明自定义密钥已启用。

验证结束后，本次临时容器、网络及数据卷均已删除；现有 Nexus 未修改。此次没有验证 `bootstrap_nexus.py`、仓库初始化或全新部署的自定义密钥初始化，因此不将完整首次部署流程标记为通过。

### 术语注解

**Blob Store**¹：Nexus 保存 Maven 制品、Docker 镜像等二进制内容的数据存储。  
**恢复点**²：能同时还原数据库元数据、二进制数据和运行配置的一组匹配备份。  
**软删除**³：先标记为删除、仍可能恢复的删除状态，尚未立即释放物理磁盘空间。  
**SHA-256**⁴：用于验证备份文件未损坏、未被替换的校验值。
**关联策略**⁵：将清理规则应用到具体仓库，使其成为 Cleanup service 的处理对象。
**CRON 表达式**⁶：用于指定定时任务执行时间的规则文本。
**预览 CSV**⁷：Cleanup Policy 根据当前条件生成的候选制品清单，不代表实际执行时的固定结果。
