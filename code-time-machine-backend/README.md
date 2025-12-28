# AI代码时光机 - 后端

> Spring Boot + JGit + AI 实现的代码历史分析后端

## 🛠️ 技术栈

- **Spring Boot 3.2** - 主框架
- **MyBatis-Plus** - ORM
- **JGit** - Git仓库解析
- **MySQL** - 数据存储
- **Hutool** - 工具类
- **Fastjson2** - JSON处理

## 📁 项目结构

```
src/main/java/com/codetimemachine/
├── CodeTimeMachineApplication.java  # 启动类
├── common/                          # 通用类
│   ├── Result.java                  # 统一响应
│   ├── PageResult.java              # 分页结果
│   ├── BusinessException.java       # 业务异常
│   └── GlobalExceptionHandler.java  # 全局异常处理
├── config/                          # 配置类
│   ├── CorsConfig.java              # 跨域配置
│   ├── MybatisPlusConfig.java       # MyBatis配置
│   └── AsyncConfig.java             # 异步配置
├── controller/                      # 控制器
│   ├── RepositoryController.java    # 仓库接口
│   ├── CommitController.java        # 提交接口
│   ├── FileController.java          # 文件接口
│   ├── AiController.java            # AI对话接口
│   └── StatsController.java         # 统计接口
├── dto/                             # 数据传输对象
│   ├── RepoOverviewDTO.java
│   └── FileTimelineDTO.java
├── entity/                          # 实体类
│   ├── Repository.java
│   ├── CommitRecord.java
│   ├── FileChange.java
│   ├── AiAnalysis.java
│   └── ChatHistory.java
├── mapper/                          # MyBatis Mapper
│   ├── RepositoryMapper.java
│   ├── CommitRecordMapper.java
│   ├── FileChangeMapper.java
│   ├── AiAnalysisMapper.java
│   └── ChatHistoryMapper.java
└── service/                         # 服务层
    ├── GitService.java              # Git解析服务
    ├── RepositoryService.java       # 仓库服务
    ├── CommitService.java           # 提交服务
    ├── FileService.java             # 文件服务
    ├── AiService.java               # AI服务
    └── impl/                        # 实现类
```

## 🚀 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+

### 2. 数据库初始化

```bash
mysql -u root -p < ../database/schema.sql
```

### 3. 修改配置

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/code_time_machine
    username: your_username
    password: your_password

app:
  ai:
    api-key: your_openai_api_key  # 可选
```

### 4. 运行

```bash
mvn spring-boot:run
```

或者打包后运行:

```bash
mvn package -DskipTests
java -jar target/code-time-machine-1.0.0.jar
```

## 📡 API接口

### 仓库管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/repository/list | 获取仓库列表 |
| GET | /api/repository/{id} | 获取仓库详情 |
| POST | /api/repository/analyze | 分析新仓库 |
| GET | /api/repository/{id}/progress | 获取分析进度 |
| GET | /api/repository/{id}/overview | 获取仓库概览 |
| DELETE | /api/repository/{id} | 删除仓库 |

### 提交记录

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/commit/list/{repoId} | 获取提交列表 |
| GET | /api/commit/{id} | 获取提交详情 |
| GET | /api/commit/{id}/files | 获取文件变更 |
| GET | /api/commit/{id}/analysis | 获取AI分析 |
| POST | /api/commit/{id}/analyze | 触发AI分析 |

### 文件操作

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/file/tree/{repoId} | 获取文件树 |
| GET | /api/file/timeline/{repoId} | 获取文件时间线 |
| GET | /api/file/content | 获取文件内容 |
| GET | /api/file/search/{repoId} | 搜索文件 |

### AI对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/ai/ask | 发送问题 |
| GET | /api/ai/history/{sessionId} | 获取对话历史 |
| DELETE | /api/ai/history/{sessionId} | 清除历史 |
| GET | /api/ai/suggestions/{commitId} | 获取推荐问题 |

### 统计数据

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/stats/lines-trend/{repoId} | 代码行数趋势 |
| GET | /api/stats/commit-frequency/{repoId} | 提交频率 |
| GET | /api/stats/contributors/{repoId} | 贡献者统计 |
| GET | /api/stats/file-types/{repoId} | 文件类型分布 |
| GET | /api/stats/change-types/{repoId} | 变更类型分布 |

## 📝 TODO

- [ ] 流式AI输出 (SSE)
- [ ] 更多AI提供商支持 (通义千问、文心一言)
- [ ] 缓存层 (Redis)
- [ ] 请求限流
- [ ] 更多统计维度
- [ ] 代码质量分析
- [ ] 私有仓库支持 (SSH Key)

## 📄 License

MIT
