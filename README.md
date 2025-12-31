# AI代码时光机（Code Time Machine）

> 像看电影一样回放代码演化过程：把 Git 仓库的提交历史、文件演化时间线、统计图表可视化，并提供 AI 解读/问答/学习路径辅助理解项目。

本仓库是一个前后端分离的全栈项目：
- `code-time-machine-backend/`：Spring Boot 后端，负责克隆/解析 Git 仓库、入库、提供 API、对接 AI。
- `code-time-machine-frontend/`：Vue 3 前端，负责交互与可视化（时间线、统计图、代码播放器、AI 聊天等）。
- `database/`：MySQL 相关 SQL（目前提供索引脚本）。

---

## 目录
- [1. 项目简介](#overview)
- [2. 功能特性](#features)
- [3. 架构与数据流](#architecture)
- [4. 技术栈](#tech-stack)
- [5. 目录结构](#structure)
- [6. 快速开始（本地开发）](#quick-start)
- [7. 配置说明](#configuration)
- [8. 数据库初始化（MySQL）](#database)
- [9. API 参考](#api)
- [10. 开发与构建](#dev-build)
- [11. 常见问题（FAQ）](#faq)
- [12. Roadmap](#roadmap)
- [13. License](#license)

---

## <a id="overview"></a>1. 项目简介

**AI代码时光机**希望解决两个常见痛点：
1. **新人上手难**：不知道从哪些文件/模块开始看，也看不懂历史上的“为什么这么改”。
2. **代码考古难**：想追溯一个文件/方法是怎么演化的、关键里程碑是哪些、何时引入了某个问题或重构。

本项目会：
- 把 Git 仓库克隆到本地并解析（提交记录、文件变更、统计数据）。
- 将解析结果落库（MySQL），提供统一 REST API。
- 前端以“时间线/播放器”的方式展示提交与文件演化，配合统计图表快速理解项目节奏与热点。
- 结合 OpenAI-compatible 的 AI 接口（也支持无 Key 的 mock 模式）：
  - 自动生成提交摘要与分类；
  - 对选中的代码/变更上下文进行问答；
  - 生成“学习路径”（推荐阅读顺序与要点）。

---

## <a id="features"></a>2. 功能特性

### 2.1 仓库分析与管理
- 通过 URL 一键分析仓库：`POST /api/repository/analyze`
- 支持分析选项：
  - `depth`：分析提交深度（如 100/500/2000/-1）
  - `since` / `until`：时间范围过滤（ISO-8601，如 `2024-01-01T00:00:00`）
  - `pathFilters`：路径过滤（如 `["src/", "*.java"]`）
  - `shallow`：浅克隆（节省时间/空间）
- 分析进度轮询：`GET /api/repository/{id}/progress`
- 支持“增量拉取更多历史”：`POST /api/repository/{id}/fetch-more-history`
- 支持删除仓库（含本地克隆目录清理）：`DELETE /api/repository/{id}`

### 2.2 提交历史浏览
- 提交列表分页 + 关键词搜索（提交信息/作者/短哈希）：`GET /api/commit/list/{repoId}`
- 查看某次提交详情、文件变更列表：`GET /api/commit/{id}` / `GET /api/commit/{id}/files`
- 按需计算并缓存提交统计（+/- 行数、变更文件数）：`GET /api/commit/{id}/stats`

### 2.3 文件演化与代码播放器
- 文件树（包含修改次数统计）：`GET /api/file/tree/{repoId}`
- 文件时间线：`GET /api/file/timeline/{repoId}?filePath=...`
- 文件内容查看（自动语言识别、行数统计）：`GET /api/file/content`
- 文件搜索：`GET /api/file/search/{repoId}?keyword=...`
- 两个提交之间的文件 diff：`GET /api/file/diff/{repoId}`
- 文件演进故事（Markdown 文本 + 关键里程碑）：`GET /api/file/evolution-story/{repoId}`
- 方法级能力：
  - 获取文件方法列表（Java 使用 JavaParser；JS/TS 使用正则）：`GET /api/file/methods/{repoId}`
  - 方法演化时间线（粗粒度，按 commit 读取内容检测方法存在）：`GET /api/file/method-timeline/{repoId}`

### 2.4 统计分析
- 代码行数趋势：`GET /api/stats/lines-trend/{repoId}`
- 提交频率：`GET /api/stats/commit-frequency/{repoId}`
- 贡献者排行：`GET /api/stats/contributors/{repoId}`
- 文件类型分布：`GET /api/stats/file-types/{repoId}`
- 变更类型分布：`GET /api/stats/change-types/{repoId}`
- 文件热度（按修改次数分层）：`GET /api/stats/file-heatmap/{repoId}`
- 活跃热力图（星期 × 小时）：`GET /api/stats/activity-heatmap/{repoId}`

### 2.5 AI 助手（可选）
- 提交 AI 分析（摘要/目的/影响/分类/复杂度/重要性）：`POST /api/commit/{id}/analyze`
- AI 对话问答：
  - 普通请求：`POST /api/ai/ask`
  - SSE 流式：`POST /api/ai/ask/stream`
- 推荐问题：`GET /api/ai/suggestions/{commitId}`
- 学习路径生成：`GET /api/ai/learning-path/{repoId}`

> 未配置 AI Key 时，后端会自动返回 mock 数据，保证核心流程可跑通（便于本地开发/演示）。

---

## <a id="architecture"></a>3. 架构与数据流

### 3.1 整体架构

```text
┌─────────────────────────────┐          ┌─────────────────────────────┐
│  Frontend (Vue3 + Vite)     │  /api    │   Backend (Spring Boot)     │
│  - 仓库/提交/文件/统计 UI    │ ───────▶ │  - Git 克隆&解析 (JGit/git)  │
│  - 代码播放器&时间线         │          │  - 数据入库 (MyBatis-Plus)   │
│  - AI 聊天/SSE              │ ◀─────── │  - AI 调用 (OpenAI兼容)      │
└─────────────────────────────┘          └──────────────┬──────────────┘
                                                        │
                                                        │ JDBC
                                                        ▼
                                              ┌─────────────────────┐
                                              │   MySQL 8.0+        │
                                              │ repository/commit/...│
                                              └─────────────────────┘
```

### 3.2 仓库分析流程（简化）
1. 前端调用 `POST /api/repository/analyze` 提交仓库 URL + 选项。
2. 后端异步任务：
   - 克隆到 `app.repo-storage-path`（默认 `./repos`，位于后端工作目录）
   - 解析仓库信息（分支、提交总数、文件数、大小等）
   - 解析 commit 列表（支持 depth/时间范围/路径过滤）
   - 批量解析 commit 的文件变更并入库（性能优化：批量获取）
3. 前端轮询 `GET /api/repository/{id}/progress` 展示进度与状态。

### 3.3 AI 调用模式
后端 AI 调用使用 **OpenAI Chat Completions 兼容接口**（`/v1/chat/completions` 风格）：
- 非流式：RestTemplate 调用，返回完整文本或 JSON。
- 流式：WebClient + `stream=true`，解析 SSE `data:` 事件中的 `choices[0].delta.content`。

---

## <a id="tech-stack"></a>4. 技术栈

### 4.1 后端
- Java 17
- Spring Boot 3.2（Web + WebFlux 用于 SSE）
- MyBatis-Plus（分页、CRUD）
- JGit（Git 仓库解析；部分能力会 fallback 到原生 git 命令）
- Caffeine（文件内容/时间线缓存）
- JavaParser（Java 方法解析）
- Fastjson2（JSON 处理）
- Hutool（工具库）
- MySQL 8.0+

### 4.2 前端
- Vue 3 + TypeScript
- Vite
- Vue Router、Pinia
- Element Plus（UI）
- ECharts + vue-echarts（可视化）
- axios（API）
- highlight.js / Prism（代码高亮相关）
- marked（Markdown 渲染）

---

## <a id="structure"></a>5. 目录结构

```text
code-time-machine/
├─ code-time-machine-backend/        # Spring Boot 后端
│  ├─ src/main/java/com/codetimemachine/
│  │  ├─ controller/                 # REST API
│  │  ├─ service/                    # 业务服务接口
│  │  ├─ service/impl/               # 业务实现（Git解析/AI/分析任务等）
│  │  ├─ entity/                     # MyBatis-Plus 实体（对应 MySQL 表）
│  │  ├─ mapper/                     # Mapper（含部分 @Select SQL）
│  │  └─ config/                     # CORS/异步线程池/缓存/MyBatis配置
│  ├─ src/main/resources/application.yml
│  └─ pom.xml
├─ code-time-machine-frontend/       # Vue 3 前端
│  ├─ src/
│  │  ├─ api/                        # API 封装（axios + fetch(SSE)）
│  │  ├─ views/                      # 页面（仓库/时间线/播放器等）
│  │  ├─ components/                 # UI 组件
│  │  ├─ stores/                     # Pinia 状态
│  │  └─ router/                     # 路由
│  ├─ vite.config.ts                 # dev server 端口 & /api 代理
│  └─ package.json
└─ database/
   └─ indexes.sql                    # 推荐索引（提升统计/列表查询性能）
```

### 关于 `code-time-machine-backend/repos/`
后端会把克隆下来的仓库放在 `app.repo-storage-path`（默认 `./repos`）。该目录已在 `.gitignore` 中忽略，属于“本地分析缓存”，可能非常大，可按需清理。

---

## <a id="quick-start"></a>6. 快速开始（本地开发）

### 6.1 环境要求
- JDK 17+
- Maven 3.8+
- Node.js 18+（Vite 相关依赖通常要求较新版本）
- MySQL 8.0+
- Git（建议安装；后端某些统计/partial clone 场景会 fallback 到 git 命令）

### 6.2 准备数据库
1) 创建数据库（建议使用 `utf8mb4`）

```sql
CREATE DATABASE IF NOT EXISTS code_time_machine
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

2) 初始化表结构与索引：见下文 [8. 数据库初始化（MySQL）](#database)。

### 6.3 启动后端
1) 修改数据库连接

编辑 `code-time-machine-backend/src/main/resources/application.yml`（示例）：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/code_time_machine?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: root
```

2)（可选）配置 AI Key（推荐使用环境变量）
- 环境变量：`AI_API_KEY`
- 或者在 `application.yml` 中设置：`app.ai.api-key`

3) 启动
```bash
cd code-time-machine-backend
mvn spring-boot:run
```

默认监听：`http://localhost:8080`

### 6.4 启动前端
```bash
cd code-time-machine-frontend
npm install
npm run dev
```

默认打开：`http://localhost:3000`

> `vite.config.ts` 已将 `/api` 代理到 `http://localhost:8080`，开发模式下前后端无需额外配置跨域。

---

## <a id="configuration"></a>7. 配置说明

### 7.1 后端配置（`application.yml`）
关键配置位于 `code-time-machine-backend/src/main/resources/application.yml`：

#### 服务端口
- `server.port`：默认 `8080`

#### 数据源
- `spring.datasource.url/username/password`

#### 应用自定义配置（`app.*`）
- `app.repo-storage-path`：Git 仓库存储路径（默认 `./repos`）
  - Windows 下建议设置为更短的路径，避免路径过长（见 FAQ）
- `app.max-repo-size`：最大仓库大小（字节，配置预留，当前版本未强制拦截）
- `app.max-commits`：最大分析提交数（配置预留；分析深度主要由 `AnalyzeOptionsDTO.depth` 控制）

#### AI 配置（`app.ai.*`）
- `app.ai.api-key`：API Key（推荐用环境变量 `AI_API_KEY` 覆盖）
- `app.ai.api-url`：OpenAI-compatible 接口地址（默认可配置为任意兼容服务）
- `app.ai.model`：模型名（例如 `gpt-4o-mini`、`Qwen/...` 等）
- `app.ai.max-tokens`：最大 tokens

> 注意：当前实现主要依赖 **OpenAI Chat Completions 兼容协议**；`app.ai.provider` 是预留字段，后续可扩展不同供应商适配。

### 7.2 前端配置
前端 API 基址来自：
- `VITE_API_BASE_URL`（优先）
- 否则默认 `/api`

开发模式代理见 `code-time-machine-frontend/vite.config.ts`：
- dev server：`3000`
- proxy：`/api -> http://localhost:8080`

---

## <a id="database"></a>8. 数据库初始化（MySQL）

本仓库 `database/` 目前提供索引脚本：`database/indexes.sql`。为了方便从零启动，下面给出一份**推荐的表结构**（可直接执行）。

> 说明：
> - 字段命名遵循 snake_case，与后端 `map-underscore-to-camel-case` 配置匹配。
> - `LONGTEXT` 用于存储 diff/文件内容/长对话；如果你不希望落库内容，可在业务层改为按需读取。

### 8.1 建表 SQL（推荐）

```sql
USE code_time_machine;

-- 仓库表
CREATE TABLE IF NOT EXISTS repository (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NULL,
  url VARCHAR(1024) NOT NULL,
  local_path VARCHAR(1024) NULL,
  description TEXT NULL,
  default_branch VARCHAR(255) NULL,
  language VARCHAR(100) NULL,
  stars INT NULL,
  total_commits INT NULL,
  total_files INT NULL,
  repo_size BIGINT NULL,
  status TINYINT NULL,              -- 0-待分析 1-分析中 2-完成 3-失败
  analyze_progress INT NULL,        -- 0-100
  analyze_depth INT NULL,           -- -1 表示全部
  analyze_since DATETIME NULL,
  analyze_path_filters TEXT NULL,   -- JSON 数组字符串
  last_analyzed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_repository_url (url),
  KEY idx_repository_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 提交记录表
CREATE TABLE IF NOT EXISTS commit_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  repo_id BIGINT NOT NULL,
  commit_hash VARCHAR(64) NOT NULL,
  short_hash VARCHAR(16) NULL,
  parent_hash VARCHAR(64) NULL,
  author_name VARCHAR(255) NULL,
  author_email VARCHAR(255) NULL,
  commit_message TEXT NULL,
  commit_time DATETIME NULL,
  additions INT NULL,
  deletions INT NULL,
  files_changed INT NULL,
  is_merge TINYINT NULL,
  commit_order INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_commit_record_repo_id (repo_id),
  KEY idx_commit_record_commit_time (commit_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文件变更表
CREATE TABLE IF NOT EXISTS file_change (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  commit_id BIGINT NOT NULL,
  repo_id BIGINT NOT NULL,
  file_path VARCHAR(1024) NOT NULL,
  file_name VARCHAR(255) NULL,
  file_extension VARCHAR(32) NULL,
  change_type VARCHAR(16) NULL,     -- ADD/MODIFY/DELETE/RENAME/COPY
  old_path VARCHAR(1024) NULL,
  additions INT NULL,
  deletions INT NULL,
  diff_text LONGTEXT NULL,
  file_content LONGTEXT NULL,
  content_hash VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_file_change_repo_id (repo_id),
  KEY idx_file_change_commit_id (commit_id),
  KEY idx_file_change_file_path (file_path(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI 分析表
CREATE TABLE IF NOT EXISTS ai_analysis (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  commit_id BIGINT NULL,
  repo_id BIGINT NULL,
  analysis_type VARCHAR(32) NULL,   -- COMMIT/FILE/QUESTION
  summary TEXT NULL,
  purpose TEXT NULL,
  impact TEXT NULL,
  technical_details LONGTEXT NULL,
  suggestions TEXT NULL,
  change_category VARCHAR(32) NULL,
  complexity_score INT NULL,
  importance_score INT NULL,
  prompt_hash VARCHAR(64) NULL,
  model_used VARCHAR(255) NULL,
  tokens_used INT NULL,
  response_time INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_analysis_repo_id (repo_id),
  KEY idx_ai_analysis_commit_id (commit_id),
  UNIQUE KEY uk_ai_analysis_commit_type (commit_id, analysis_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 聊天记录表
CREATE TABLE IF NOT EXISTS chat_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  repo_id BIGINT NULL,
  commit_id BIGINT NULL,
  file_path VARCHAR(1024) NULL,
  role VARCHAR(16) NOT NULL,        -- user/assistant/system
  content LONGTEXT NULL,
  tokens_used INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_chat_history_session_time (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 8.2 推荐索引（仓库已提供）
执行 `database/indexes.sql`（需要先建表）：

```bash
mysql -u root -p code_time_machine < database/indexes.sql
```

---

## <a id="api"></a>9. API 参考

### 9.1 统一响应格式
后端所有接口统一包一层 `Result<T>`：

成功：
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

失败（示例）：
```json
{
  "code": 500,
  "message": "错误信息",
  "data": null
}
```

分页结果 `PageResult<T>`：
```json
{
  "list": [],
  "total": 0,
  "page": 1,
  "pageSize": 10
}
```

> 下文仅展示 `data` 的结构要点；实际返回会被 `Result` 包裹。

### 9.2 仓库（Repository）

#### 获取仓库列表
- `GET /api/repository/list?page=1&pageSize=10`

#### 分析仓库（支持选项）
- `POST /api/repository/analyze`
- Body 示例：
```json
{
  "url": "https://github.com/vuejs/core.git",
  "depth": 500,
  "since": "2024-01-01T00:00:00",
  "until": "2024-12-31T23:59:59",
  "pathFilters": ["packages/", "*.ts"],
  "shallow": true
}
```

#### 获取分析进度
- `GET /api/repository/{id}/progress`
- 返回字段：`progress`（0-100 / -1失败）、`status`（0-3）

#### 仓库概览
- `GET /api/repository/{id}/overview`
- 返回：总提交数、贡献者 Top、总增删行、首次/最后提交时间等

#### 增量加载更多历史
- `POST /api/repository/{id}/fetch-more-history`
- Body：`{ "depth": 500 }`

#### 删除仓库
- `DELETE /api/repository/{id}`

---

### 9.3 提交（Commit）

#### 提交列表（分页 + 搜索）
- `GET /api/commit/list/{repoId}?page=1&pageSize=50&keyword=fix`

#### 提交详情
- `GET /api/commit/{id}`

#### 提交的文件变更
- `GET /api/commit/{commitId}/files`

#### 提交统计（按需计算并缓存）
- `GET /api/commit/{commitId}/stats`
- 返回：`additions/deletions/filesChanged/calculated`

#### 获取提交 AI 分析
- `GET /api/commit/{commitId}/analysis`
- 若无分析，返回 `404` 错误码

#### 触发提交 AI 分析
- `POST /api/commit/{commitId}/analyze`

---

### 9.4 文件（File）

#### 文件树（含修改次数）
- `GET /api/file/tree/{repoId}`

#### 文件时间线
- `GET /api/file/timeline/{repoId}?filePath=src/main.ts&includeContent=false`
- `includeContent=true` 时会携带每个关键版本的文件内容（可能较大）

#### 文件内容
- `GET /api/file/content?repoId=1&commitId=123&filePath=src/main.ts`
- 返回：`content/language/lineCount`

#### 文件搜索
- `GET /api/file/search/{repoId}?keyword=controller`

#### 文件演进故事（Markdown）
- `GET /api/file/evolution-story/{repoId}?filePath=src/main.ts`
- 返回：`story`（Markdown 文本）、`keyMilestones`（关键里程碑列表）

#### 两提交之间 diff（按 commit hash）
- `GET /api/file/diff/{repoId}?fromCommit=abc123&toCommit=def456&filePath=src/main.ts`

#### 文件方法列表
- `GET /api/file/methods/{repoId}?commitId=123&filePath=src/main/java/com/foo/Bar.java`

#### 方法演化时间线
- `GET /api/file/method-timeline/{repoId}?filePath=src/main/java/com/foo/Bar.java&methodName=doWork`

---

### 9.5 AI（可选）

#### AI 问答
- `POST /api/ai/ask`
- Body 示例：
```json
{
  "sessionId": "demo-session",
  "repoId": 1,
  "commitId": 123,
  "filePath": "src/main.ts",
  "question": "这次改动的目的是什么？",
  "context": "这里放 diff/代码片段/你希望 AI 参考的上下文"
}
```

#### AI 问答（SSE 流式）
- `POST /api/ai/ask/stream`
- Header：`Accept: text/event-stream`
- curl 示例（注意 `-N` 关闭缓冲）：
```bash
# bash/zsh
curl -N "http://localhost:8080/api/ai/ask/stream" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"sessionId":"demo","question":"你好","context":""}'

# Windows PowerShell（避免 curl 别名：使用 curl.exe）
curl.exe -N "http://localhost:8080/api/ai/ask/stream" `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  -d '{"sessionId":"demo","question":"你好","context":""}'
```

#### 对话历史
- `GET /api/ai/history/{sessionId}`
- `DELETE /api/ai/history/{sessionId}`

#### 推荐问题
- `GET /api/ai/suggestions/{commitId}`

#### 学习路径
- `GET /api/ai/learning-path/{repoId}`

---

### 9.6 统计（Stats）
- `GET /api/stats/lines-trend/{repoId}`
- `GET /api/stats/commit-frequency/{repoId}`
- `GET /api/stats/contributors/{repoId}`
- `GET /api/stats/file-types/{repoId}`
- `GET /api/stats/change-types/{repoId}`
- `GET /api/stats/file-heatmap/{repoId}`
- `GET /api/stats/activity-heatmap/{repoId}`

---

## <a id="dev-build"></a>10. 开发与构建

### 10.1 后端
常用命令：
```bash
cd code-time-machine-backend
mvn spring-boot:run
```

打包：
```bash
mvn package -DskipTests
java -jar target/code-time-machine-1.0.0.jar
```

### 10.2 前端
常用命令：
```bash
cd code-time-machine-frontend
npm run dev
npm run build
npm run preview
```

### 10.3 生产部署建议（参考）
- 后端以 `8080` 提供 API
- 前端打包产物为 `code-time-machine-frontend/dist/`
- 推荐用 Nginx 统一入口：
  - 静态资源指向 `dist/`
  - `/api` 反代到后端

---

## <a id="faq"></a>11. 常见问题（FAQ）

### Q1：启动后端提示表不存在 / 查询报错？
需要先初始化 MySQL 表结构与索引。参考：
- [8. 数据库初始化（MySQL）](#database)

### Q2：前端请求 `/api/...` 报 404？
排查顺序：
1. 后端是否启动在 `http://localhost:8080`
2. 前端是否通过 Vite 开发服务器启动（它会代理 `/api`）
3. 如果是生产环境，确认是否做了 `/api` 反代或设置 `VITE_API_BASE_URL`

### Q3：克隆仓库失败？
常见原因：
- URL 不可达/网络问题
- 私有仓库需要鉴权（当前版本主要支持公开仓库；可尝试在 URL 中携带 token，但注意安全风险）

### Q4：Windows 下路径过长导致异常？
分析仓库时会产生很深的目录结构，Windows 默认路径长度限制可能触发问题。建议：
- 将 `app.repo-storage-path` 配置为更短的路径（例如 `D:/repos`）
- 启用 Windows Long Paths（组策略/注册表）并在 git 中开启 `core.longpaths=true`

### Q5：不配置 AI Key 能用吗？
可以。后端检测到 `app.ai.api-key` 为空（或以 `sk-xxx` 开头）时，会返回 mock 分析/回答，用于演示与本地开发。

---

## <a id="roadmap"></a>12. Roadmap

已在代码中标注的一些方向（可作为后续迭代清单）：
- 更完善的流式 AI 输出（SSE 事件结构/前端渲染优化）
- 适配更多 AI 供应商（通义千问、文心一言等）与更严格的 Token 统计
- 缓存层（Redis）与请求限流
- 更丰富的统计维度（热力图、趋势、质量分析）
- 更完整的私有仓库支持（SSH Key / Token 管理）

---

## <a id="license"></a>13. License

后端模块 `code-time-machine-backend/README.md` 标注为 MIT。当前仓库根目录未提供 `LICENSE` 文件；如计划开源发布，建议补充标准 `LICENSE` 文本以避免歧义。
