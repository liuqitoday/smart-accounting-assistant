# 智能记账助手 (Accounting Assistant)

<div align="center">

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5+-green.svg)](https://vuejs.org/)

一个前后端一体的智能记账应用，使用自然语言即可完成记账。

[特性](#特性) • [快速开始](#快速开始) • [部署](#部署) • [文档](#文档) • [贡献](#贡献)

</div>

## 特性

**AI 自然语言解析** - 输入"今天中午和同事在海底捞吃火锅花了320"，自动提取金额、类型、商户、分类

**AI 智能分析** - 用自然语言提问"本月餐饮花了多少"、"收入最多的是哪个月"，AI 自动生成数据分析

**多账本管理** - 创建多个账本（个人、家庭、旅行），邀请成员，不同角色权限

**账户管理** - 支持银行卡、支付宝、微信、现金等多账户，账户间转账

**标签系统** - 自定义标签 + 系统预置标签，按标签筛选

**统计分析** - 收支汇总（含环比）、分类构成饼图、月度/每日趋势图、大额支出排行

**CSV 导入导出** - 支持外部数据导入，支持按条件筛选导出

**安全可靠** - 密码加密存储、Session + Remember-me 双重登录态、CSRF 防护

**SQLite 数据库** - 零配置，备份就是复制一个文件

## 技术栈

**后端：** Spring Boot 3.5.4 + Spring AI + Spring Security + JPA + SQLite

**前端：** Vue 3 + TypeScript + Vite + Chart.js + Lucide Icons

**AI：** 支持 OpenAI 及兼容服务（如 Deepseek 等）

## 前置要求

- **Java 17+** - [下载 OpenJDK](https://adoptium.net/)
- **Maven 3.8+** - 或使用项目内的 `./mvnw`
- **Node.js 22+ / npm** - 仅本地构建前端时需要
- **AI API Key** - OpenAI API Key 或兼容服务的 API Key

## 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/liuqitoday/smart-accounting-assistant.git
cd smart-accounting-assistant
```

### 2. 配置 AI 服务

**本地运行**（`java -jar` 或 `./mvnw spring-boot:run`）：在**项目根目录**创建 `application-local.yml`（已被 `.gitignore` 忽略，不会提交）。Spring Boot 会自动加载该文件，**API Key 缺失时应用会启动失败**：

```yaml
spring:
  ai:
    openai:
      api-key: sk-your-api-key-here
      base-url: https://api.openai.com  # OpenAI 官方
      # base-url: https://api.deepseek.com  # Deepseek
      chat:
        options:
          model: gpt-3.5-turbo  # 或 gpt-4, deepseek-chat 等
```

> `.env` 文件**只被 `docker compose` 读取**，本地 `java -jar` / `spring-boot:run` 不会加载它；本地运行请用上面的 `application-local.yml`。

**Docker / Docker Compose 部署**：复制 `.env.example` 为 `.env` 并填写配置：

```bash
cp .env.example .env
```

**AI 服务选项：**

| 服务 | base-url | model 示例 | 备注 |
|------|----------|-----------|------|
| OpenAI | `https://api.openai.com` | `gpt-3.5-turbo`, `gpt-4` | 官方服务 |
| Deepseek | `https://api.deepseek.com` | `deepseek-chat` | 国内可用 |

### 3. 构建并运行

```bash
# 完整构建（包含前端）
./mvnw clean package

# 运行
java -jar target/accounting-assistant-0.0.1-SNAPSHOT.jar
```

浏览器打开 `http://localhost:8081`，注册账号即可使用。

### 4. 本地开发（前后端分离）

```bash
# 终端 1：后端（跳过前端构建，加速启动）
./mvnw -Dskip.frontend=true spring-boot:run

# 终端 2：前端 Vite 开发服务器
cd frontend
npm install
npm run dev
```

前端监听 `http://localhost:5173`，API 请求自动代理到后端 `8081` 端口。

## 部署

### Docker 部署（推荐）

1. **使用预构建镜像（最省事）**

打 `v*` tag 时 CI 会自动构建 `linux/amd64` + `linux/arm64` 多架构镜像并推送到两个 registry，直接拉取即可（无需本地构建）：

```bash
# GitHub Container Registry
docker pull ghcr.io/liuqitoday/smart-accounting-assistant:latest

# Docker Hub
docker pull liuqitoday/smart-accounting-assistant:latest
```

运行（镜像地址换成上面任一）：

```bash
docker run -d --name accounting-assistant \
  -p 8081:8081 \
  -v $(pwd)/data:/app/data \
  -e SPRING_AI_OPENAI_API_KEY=sk-your-key \
  -e REMEMBER_ME_KEY=$(openssl rand -hex 32) \
  ghcr.io/liuqitoday/smart-accounting-assistant:latest
```

2. **使用 Docker Compose**

```bash
# 创建配置文件
cp .env.example .env
# 编辑 .env 填入你的 API Key

# 启动服务（会基于本地源码构建镜像）
docker compose up -d

# 查看日志
docker compose logs -f

# 停止服务
docker compose down
```

访问 `http://localhost:8081`

3. **手动构建镜像**

```bash
# 构建镜像
docker build -t accounting-assistant .

# 运行容器
docker run -d \
  --name accounting-assistant \
  -p 8081:8081 \
  -v $(pwd)/data:/app/data \
  -e SPRING_AI_OPENAI_API_KEY=sk-your-key \
  -e REMEMBER_ME_KEY=$(openssl rand -hex 32) \
  accounting-assistant
```

**内存调优（可选）：** 容器内直接通过 JVM 原生的 `JAVA_TOOL_OPTIONS` 传入参数，例如 `-e JAVA_TOOL_OPTIONS="-Xmx512m -Xms256m"`（`JAVA_OPTS` 仅 systemd 部署使用）。

### 传统部署

**systemd 服务（Linux）**

1. 创建 jar 包：

```bash
./mvnw clean package
```

2. 创建服务文件 `/etc/systemd/system/accounting-assistant.service`：

```ini
[Unit]
Description=Accounting Assistant
After=network.target

[Service]
Type=simple
User=your-user
WorkingDirectory=/opt/accounting-assistant
Environment="SPRING_AI_OPENAI_API_KEY=sk-your-key"
Environment="REMEMBER_ME_KEY=change-me-random-hex"
Environment="COOKIE_SECURE=true"
ExecStart=/usr/bin/java -jar /opt/accounting-assistant/accounting-assistant-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

3. 启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable accounting-assistant
sudo systemctl start accounting-assistant
```

### HTTPS 配置

如果使用 Nginx 反向代理终止 SSL：

```nginx
server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

并设置环境变量：

```bash
export COOKIE_SECURE=true
```

## 环境变量

| 变量名 | 说明 | 默认值 | 必需 |
|--------|------|--------|------|
| `SPRING_AI_OPENAI_API_KEY` | AI 服务 API Key | 无 | 是 |
| `SPRING_AI_OPENAI_BASE_URL` | AI 服务 Base URL | `https://api.openai.com` | 否 |
| `SPRING_AI_OPENAI_MODEL` | AI 模型名称 | `gpt-3.5-turbo` | 否 |
| `REMEMBER_ME_KEY` | Remember-me 令牌签名密钥，生产环境务必设为随机值（`openssl rand -hex 32`） | 内置固定值（不安全，仅供本地） | 生产必填 |
| `COOKIE_SECURE` | 启用 HTTPS Secure Cookie | `false` | 否 |
| `SERVER_PORT` | 服务端口 | `8081` | 否 |
| `SPRING_DATASOURCE_URL` | 数据库路径 | `jdbc:sqlite:accounting-assistant.db?journal_mode=WAL&busy_timeout=5000&synchronous=NORMAL` | 否 |

## 文档

- [API 文档](docs/API.md) - 常用接口与统一响应格式
- [部署指南](docs/DEPLOYMENT.md) - Docker / systemd / 反向代理 / 备份
- [贡献指南](docs/CONTRIBUTING.md) - 开发环境、代码规范、PR 流程
- [交易筛选、导出与导入指南](docs/FILTER_AND_EXPORT_GUIDE.md)
- [标签与 CSV 导入指南](docs/TAG_AND_IMPORT_GUIDE.md)

## 数据库

使用 SQLite 文件数据库 `accounting-assistant.db`，应用启动时自动创建表和初始化数据。

**备份：**

```bash
# 备份
cp accounting-assistant.db accounting-assistant-$(date +%Y%m%d).db

# 恢复（先停应用）
cp accounting-assistant-20260116.db accounting-assistant.db
```

**查看数据：**

```bash
sqlite3 accounting-assistant.db ".tables"
sqlite3 accounting-assistant.db "SELECT * FROM transactions LIMIT 10;"
```

**核心表：**

- `users` - 用户
- `ledgers` - 账本
- `ledger_members` - 账本成员
- `transactions` - 交易记录
- `categories` - 分类
- `accounts` - 账户
- `tags` - 标签

## 项目结构

```text
smart-accounting-assistant/
├── frontend/                      # Vue 3 前端
│   ├── src/
│   │   ├── api/                   # HTTP 封装 + API 函数
│   │   ├── components/            # 通用组件
│   │   ├── stores/                # 状态管理（reactive 单例）
│   │   ├── utils/                 # 工具函数
│   │   └── views/                 # 页面视图
│   ├── public/                    # 静态资源
│   └── vite.config.ts
├── src/main/java/
│   └── com/liuqitech/accountingassistant/
│       ├── config/                # 配置类
│       ├── controller/            # REST 控制器
│       ├── dto/                   # 请求/响应 DTO
│       ├── entity/                # JPA 实体
│       ├── enums/                 # 枚举
│       ├── exception/             # 全局异常处理
│       ├── interceptor/           # 拦截器（账本上下文）
│       ├── repository/            # JPA Repository
│       ├── security/              # Spring Security 配置
│       ├── service/               # 业务逻辑 + AI 解析
│       └── util/                  # 工具类
├── src/main/resources/
│   ├── application.yml            # 主配置
│   ├── data.sql                   # 初始化数据
│   └── logback-spring.xml         # 日志配置
├── docs/                          # 文档（API/部署/贡献/功能指南）
├── .env.example                   # 环境变量示例
├── docker-compose.yml
├── Dockerfile
├── LICENSE                        # MIT 协议
├── pom.xml
└── README.md
```

## 常见问题

**Q: 启动后前端页面 404？**

A: 需要先完整构建（`mvn clean package`）或本地开发时启动前端 Vite 服务器。

**Q: SQLite 报 database is locked？**

A: 确认只有一个应用实例在写入。可执行：

```bash
sqlite3 accounting-assistant.db "PRAGMA journal_mode=WAL;"
```

**Q: AI 解析失败？**

A: 检查：
1. API Key 是否正确配置
2. Base URL 是否可访问
3. 模型名称是否正确
4. 查看日志 `account-assistant-logs/application.log` 或控制台输出

**Q: 如何切换 AI 服务商？**

A: 修改 `application-local.yml` 或环境变量：

```yaml
spring:
  ai:
    openai:
      api-key: your-new-key
      base-url: https://new-provider.com
      chat:
        options:
          model: new-model-name
```

**Q: 如何备份数据？**

A: 直接复制 `accounting-assistant.db` 文件即可。Docker 部署时数据在 `./data/` 目录。

## 贡献

欢迎贡献代码、报告 Bug、提出新功能建议！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

详见 [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md)

## 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。

## 致谢

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Spring AI](https://spring.io/projects/spring-ai)
- [Vue.js](https://vuejs.org/)
- [Chart.js](https://www.chartjs.org/)
- [Lucide Icons](https://lucide.dev/)

## 联系方式

- 问题反馈：[GitHub Issues](https://github.com/liuqitoday/smart-accounting-assistant/issues)
- 功能建议：[GitHub Discussions](https://github.com/liuqitoday/smart-accounting-assistant/discussions)

---

如果这个项目对你有帮助，欢迎 Star 支持！
