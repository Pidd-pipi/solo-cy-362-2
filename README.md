# 剧本杀门店运营管理系统

面向剧本杀线下门店，提供剧本库管理、场次排期、玩家组局、DM排班和会员运营等一站式门店运营工具。

## Docker Compose 快速启动

首次启动前复制环境变量文件：

```bash
cp .env.example .env
docker compose up -d
```

访问地址：

- 前端：http://localhost:28502
- 后端健康检查：http://localhost:29502/health
- API 示例：http://localhost:28502/api/overview

## 自动化测试

一键运行前后端全部测试（后端用内嵌 H2，前端用 happy-dom，**不依赖外部数据库、网络或人工操作**，可重复执行）：

```bash
./run-tests.sh
```

也可以分别运行：

```bash
cd backend && mvn test          # 后端：JUnit5 + Spring MockMvc + H2，15 个用例
cd frontend && npm test         # 前端：Vitest + happy-dom，19 个用例
```

覆盖场景：

- **剧本候选同步**：剧本新增、编辑、停用、启用后，发布场次的候选列表无需刷新页面即同步（后端接口语义 + 前端共享 store 与双组件真实挂载两层验证）。
- **刷新竞态防护（前端共享 store）**：首次加载在途时保存触发的刷新不会复用保存前的旧请求；同 tick 并发刷新合并为一次；响应乱序不会用旧结果覆盖较新数据；请求失败不清空已有数据且下一次刷新可恢复。通过可手动控制延迟/乱序/失败的假后端精确复现这四种时序。
- **报名主流程**：正常报名（剩余名额立即更新）、重复报名被拒、满员不能报名、取消后空位释放并可重报。
- **并发抢最后一个名额**：8 人同时抢 1 个空位，断言恰好 1 人成功、其余收到「已满员」、最终不超卖；另含同一玩家双击并发只成功一次、取消释放空位后多人并发补位只进一人。

测试失败时的定位方式：

- 后端：控制台与 `backend/target/surefire-reports/` 给出失败的**类名/方法名 + 中文场景名（@DisplayName）+ 源码行号 + expected/but was**；并发用例的断言信息还会打印实际成功数与各失败原因。
- 前端：Vitest 直接打印失败的 `describe/it` 场景名（断言描述中写明“期望”）、源码位置与实际值。
- 每个测试方法执行前自动清库重置序列，前端每个用例重置模块状态，因此**连续执行多遍结果一致**。

## 项目主要功能

- 剧本库与DM管理：录入剧本信息（名称、类型、难度、时长、人数、主持人DM要求），上传剧本封面与简介，关联专属DM主持人，支持按标签筛选与搜索。
- **剧本排期与拼车报名（已实现）**：门店可新增、编辑、停用剧本，按名称、类型、难度查找；发布场次时选择剧本、开场时间、主持人与人数上限。玩家可报名、取消报名，每次操作后剩余名额立即更新；满员不能报名，取消后空位立即释放可再报；重复报名、满员、参数错误等场景均返回明确中文原因。
- 玩家组局与角色分配：满局后DM可为玩家分配角色，支持随机分配与手动调整，系统记录每次组局玩家名单与角色分配结果。
- 会员积分与等级体系：注册会员消费积累积分，设置等级规则（如青铜/白银/黄金/钻石），不同等级享受折扣与优先拼车位权益，积分可兑换周边或抵扣费用。
- 营收与上座率分析：管理员查看每日/周/月营收报表、各剧本上座率排行、DM带本场次与评分统计，支持导出营业数据。

### 排期与拼车报名模块 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/scripts?name=&genre=&difficulty=&includeInactive=` | 剧本列表，支持按名称（模糊）、类型、难度查找 |
| POST | `/api/scripts` | 新增剧本 |
| PUT | `/api/scripts/{id}` | 编辑剧本 |
| PATCH | `/api/scripts/{id}/active` | 停用 / 启用剧本（停用后不能发布新场次） |
| GET | `/api/sessions` | 场次列表，含已报名人数、剩余名额、是否满员与报名名单 |
| POST | `/api/sessions` | 发布场次（剧本、开场时间、主持人、人数上限） |
| POST | `/api/sessions/{id}/registrations` | 玩家报名（请求体：`playerName`、`contact?`） |
| DELETE | `/api/sessions/{id}/registrations?playerName=` | 玩家取消报名，空位立即释放 |

业务规则（并发安全）：报名与取消在数据库事务内对场次行加排他锁后再校验名额，唯一约束 `(session_id, player_name)` 兜底重复报名；因此「重复报名」「满员报名」「取消不存在的报名」都会返回 HTTP 400 与中文原因。

### 本地不依赖数据库快速验证

后端默认使用 H2 内存库（启动时自动建表并写入种子剧本/场次），无需 PostgreSQL 即可运行：

```bash
cd backend
mvn spring-boot:run
# 正常报名
curl -X POST http://localhost:29502/api/sessions/1/registrations \
  -H 'Content-Type: application/json' -d '{"playerName":"玩家A"}'
# 再次提交相同玩家 -> 400：玩家「玩家A」已报名该场次，不能重复报名
# 报满后再报名 -> 400：该场次已满员（6/6），无法报名
# 取消后空位立即可再报
curl -X DELETE 'http://localhost:29502/api/sessions/1/registrations?playerName=%E7%8E%A9%E5%AE%B6A'
```


## 本地开发方式

前端：

```bash
cd frontend
npm install
npm run dev
```

后端：

```bash
cd backend
mvn spring-boot:run
```

## 技术栈

| 分层 | 技术 |
| --- | --- |
| 前端 | Vue 3 + TypeScript、Element Plus、Vite |
| 后端 | Spring Boot + Java |
| 数据库 | PostgreSQL |
| 认证 | JWT |
| 依赖 | MyBatis、Maven |

## 项目目录结构

```text
.
├── backend/              # 后端服务
├── database/             # 数据库脚本
├── frontend/             # 前端应用
├── docker-compose.yml    # 一键部署编排
├── .env.example          # 环境变量示例
└── README.md
```

## 环境变量说明

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| COMPOSE_PROJECT_NAME | Compose 项目名，避免中文目录名导致项目名为空 | ldmurdergame |
| DB_NAME | 数据库名称 | app |
| DB_USER | 数据库用户 | app |
| DB_PASSWORD | 数据库密码 | app_pwd |
| DB_ROOT_PASSWORD | 数据库 root 密码 | root_pwd |
| JWT_SECRET | JWT 签名密钥 | change_me_to_a_long_random_string |
| FRONTEND_PORT | 前端宿主机端口 | 28502 |
| BACKEND_PORT | 后端宿主机端口 | 29502 |
| DB_PORT | 数据库宿主机端口 | 5432 |

## Docker 部署说明

- 使用 `docker compose up -d` 启动，不需要额外传入 `-p`。
- `docker-compose.yml` 顶层已声明 `name: ldmurdergame`，并且 `.env` 包含 `COMPOSE_PROJECT_NAME=ldmurdergame`，可在中文目录名下启动。
- 数据库数据保存在命名卷 `db_data` 中，不依赖当前目录名。
- 前端容器由 Nginx 托管静态资源，并把 `/api/` 反向代理到 `backend:29502`。
- 若本地端口冲突，可修改 `.env` 中的 `FRONTEND_PORT`、`BACKEND_PORT`、`DB_PORT`。

常用命令：

```bash
docker compose config --quiet
docker compose ps
docker compose down
```

## License

MIT
