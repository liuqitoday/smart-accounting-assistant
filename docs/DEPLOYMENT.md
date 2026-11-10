# 部署指南

本文档提供了详细的部署方案，包括 Docker、systemd、反向代理等。

## 目录

- [Docker 部署](#docker-部署)
- [Docker Compose 部署](#docker-compose-部署)
- [截图记账配置](#截图记账配置)
- [传统部署](#传统部署)
- [反向代理配置](#反向代理配置)
- [备份和恢复](#备份和恢复)
- [监控和日志](#监控和日志)
- [性能优化](#性能优化)

## Docker 部署

### 1. 构建镜像

```bash
# 克隆仓库
git clone https://github.com/liuqitoday/smart-accounting-assistant.git
cd smart-accounting-assistant

# 构建镜像
docker build -t accounting-assistant:latest .
```

### 2. 运行容器

```bash
# 创建数据目录
mkdir -p ./data

# 运行容器
docker run -d \
  --name accounting-assistant \
  --restart unless-stopped \
  -p 8081:8081 \
  -v $(pwd)/data:/app/data \
  -e SPRING_AI_OPENAI_API_KEY=sk-your-api-key \
  -e SPRING_AI_OPENAI_BASE_URL=https://api.openai.com \
  -e SPRING_AI_OPENAI_MODEL=gpt-3.5-turbo \
  -e REMEMBER_ME_KEY=$(openssl rand -hex 32) \
  -e COOKIE_SECURE=false \
  accounting-assistant:latest
```

### 3. 查看日志

```bash
docker logs -f accounting-assistant
```

### 4. 停止和删除

```bash
docker stop accounting-assistant
docker rm accounting-assistant
```

## Docker Compose 部署（推荐）

### 1. 创建配置文件

```bash
cp .env.example .env
```

编辑 `.env`：

```bash
SPRING_AI_OPENAI_API_KEY=sk-your-api-key
SPRING_AI_OPENAI_BASE_URL=https://api.openai.com
SPRING_AI_OPENAI_MODEL=gpt-3.5-turbo
AI_MULTIMODAL_ENABLED=false
REMEMBER_ME_KEY=change-me-to-random-hex-string
COOKIE_SECURE=false
```

### 2. 启动服务

```bash
docker compose up -d
```

### 3. 查看状态

```bash
# 查看运行状态
docker compose ps

# 查看日志
docker compose logs -f

# 查看实时日志（特定服务）
docker compose logs -f app
```

### 4. 停止服务

```bash
docker compose down
```

### 5. 更新服务

```bash
# 拉取最新代码
git pull

# 重新构建并启动
docker compose up -d --build
```

## 截图记账配置

截图记账默认关闭。它复用现有 AI 服务的 base URL、API key、模型、超时与重试设置，开启前需确认**当前模型及服务商的 OpenAI 兼容接口都支持图片输入**（vision / `image_url`）。开关只控制功能入口和后端授权，不会自动替换模型或赋予文本模型图片能力。

### 配置方式

Docker Compose 会将 `.env` 中的 `AI_MULTIMODAL_ENABLED` 传入容器，未配置时采用 `false`。使用支持图片的模型时，在已有 `.env` 中设置以下项目；示例值均为占位符，应替换为实际可用配置：

```dotenv
SPRING_AI_OPENAI_API_KEY=your-api-key
SPRING_AI_OPENAI_BASE_URL=https://your-provider.example
SPRING_AI_OPENAI_MODEL=your-vision-model
AI_MULTIMODAL_ENABLED=true
```

更改 `.env` 后重新创建容器，让新的环境变量生效：

```bash
docker compose up -d --force-recreate
```

若同时更新了本地源代码，使用 `docker compose up -d --build` 重新构建并启动。仅执行 `docker compose restart` 不会重新读取容器的环境配置。

直接运行 Docker 镜像时增加 `-e AI_MULTIMODAL_ENABLED=true`，同时通过已有的 `SPRING_AI_OPENAI_*` 环境变量配置图片模型。systemd 部署时在服务的环境配置中增加 `Environment="AI_MULTIMODAL_ENABLED=true"`，然后执行 `systemctl daemon-reload` 和 `systemctl restart accounting-assistant`。

本地 `java -jar` / `spring-boot:run` 不会读取 `.env`。可以在进程环境中设置 `AI_MULTIMODAL_ENABLED=true`，或在根目录的 `application-local.yml` 中追加以下配置，保留原有 `spring.ai.openai` 配置：

```yaml
app:
  ai:
    multimodal-enabled: true
```

配置变更后重启应用。关闭时将相应开关改为 `false` 并重启；前端会隐藏“截图记账”入口，后端也会拒绝直接调用图片识别。不要把真实 API key 写入公开配置文件或提交到 Git。

### 上传限制与反向代理

| 项目 | 限制 |
|---|---|
| 文件类型 | 真实且完整的 PNG / JPEG 图片 |
| 单张文件大小 | 5 MiB（5,242,880 字节） |
| 图片像素总数 | 20,000,000 像素（宽 × 高） |
| 单张识别笔数 | 最多 20 笔收入或支出 |
| 应用 multipart 限制 | `max-file-size: 5MB`、`max-request-size: 6MB` |
| 代理/网关请求体限制 | 至少 6 MiB，给 multipart 边界预留空间 |

例如 Nginx 配置 `client_max_body_size 6M;` 或更大值。本指南下方的 `10M` 配置已满足要求。如果还经过 CDN、Ingress 或其它网关，也需要检查相应请求体限制；代理拒绝上传时，应用不会收到请求。

默认 AI 连接超时为 5 秒，读取超时为 60 秒；图片识别与文字解析使用相同的模型重试配置。代理等待响应的超时应覆盖可能的模型等待和重试，下方 Nginx 示例使用 `proxy_read_timeout 180s`。不要只增大代理上传限制而忽略等待模型响应所需的时间。

### 使用与验证

重启后登录并选择一个可编辑账本，从智能记账首页或移动端底部“＋”打开统一记账面板，选择“截图”。桌面端支持文件选择、拖入和粘贴，移动端支持从相册选择；上传一张截图后点击识别，核对原图与结果，再逐笔确认保存或跳过。

识别结果只是待确认记录，不会自动入账。缺少完整日期时需补齐日期；分类会匹配当前分类及账本历史纠正记录；账户只在当前账本的启用账户中按名称唯一匹配，不确定时保持未指定，由用户选择。图片会发送给配置的模型服务，应用本身不持久保存原图；确认保存后，交易字段及相关识别文字按正常交易记录存储。

图片识别和文字解析共用 `app.ai.daily-limit`（默认每用户每天 200 次，按上海时间重置），一张通过校验的图片计一次识别，模型调用失败也计次；逐笔保存不消耗 AI 额度。只读账本成员不能识别或保存图片交易。

可使用登录会话调用 `GET /api/transactions/capabilities`，检查 `data.imageParsingEnabled` 是否为 `true`，以及服务端返回的文件类型、大小与笔数限制。该接口不会暴露模型凭据。完整请求示例及错误码见 [API 文档](API.md#截图记账)。

## 传统部署

### 1. 准备环境

**系统要求：**
- Linux 服务器（Ubuntu 20.04+ / CentOS 8+ 推荐）
- Java 17 或更高版本
- 本地构建需要 Maven 3.9+（或 `./mvnw`）与 Node.js 22+ / npm
- 至少 512MB 可用内存
- 至少 1GB 可用磁盘空间

**安装 Java：**

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y openjdk-17-jre

# CentOS/RHEL
sudo yum install -y java-17-openjdk

# 验证安装
java -version
```

### 2. 构建应用

在本地机器上构建：

```bash
./mvnw clean package
```

生成的 jar 文件位于 `target/accounting-assistant-0.0.1-SNAPSHOT.jar`

### 3. 创建系统用户与部署目录（可选但推荐）

```bash
sudo useradd -r -s /bin/false accounting
sudo mkdir -p /opt/accounting-assistant
sudo chown accounting:accounting /opt/accounting-assistant
```

### 4. 上传到服务器

先上传到临时目录，再由 root 移动到部署目录并授权（`/opt/accounting-assistant` 归 `accounting` 用户所有，普通用户无法直接写入）：

```bash
scp target/accounting-assistant-0.0.1-SNAPSHOT.jar user@your-server:/tmp/
ssh user@your-server 'sudo mv /tmp/accounting-assistant-0.0.1-SNAPSHOT.jar /opt/accounting-assistant/ && sudo chown accounting:accounting /opt/accounting-assistant/accounting-assistant-0.0.1-SNAPSHOT.jar'
```

### 5. 配置 systemd 服务

创建 `/etc/systemd/system/accounting-assistant.service`：

```ini
[Unit]
Description=Accounting Assistant
Documentation=https://github.com/liuqitoday/smart-accounting-assistant
After=network.target

[Service]
Type=simple
User=accounting
Group=accounting
WorkingDirectory=/opt/accounting-assistant

# 环境变量配置
Environment="SPRING_AI_OPENAI_API_KEY=sk-your-api-key"
Environment="SPRING_AI_OPENAI_BASE_URL=https://api.openai.com"
Environment="SPRING_AI_OPENAI_MODEL=gpt-3.5-turbo"
Environment="REMEMBER_ME_KEY=your-random-hex-string"
Environment="COOKIE_SECURE=true"
Environment="SERVER_PORT=8081"

# JVM 参数
Environment="JAVA_OPTS=-Xmx512m -Xms256m"

# 启动命令
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/accounting-assistant/accounting-assistant-0.0.1-SNAPSHOT.jar

# 重启策略
Restart=on-failure
RestartSec=10

# 安全设置
PrivateTmp=true
NoNewPrivileges=true

# 日志
StandardOutput=journal
StandardError=journal
SyslogIdentifier=accounting-assistant

[Install]
WantedBy=multi-user.target
```

### 6. 启动服务

```bash
# 重载 systemd 配置
sudo systemctl daemon-reload

# 启用开机自启
sudo systemctl enable accounting-assistant

# 启动服务
sudo systemctl start accounting-assistant

# 查看状态
sudo systemctl status accounting-assistant

# 查看日志
sudo journalctl -u accounting-assistant -f
```

### 7. 管理服务

```bash
# 停止服务
sudo systemctl stop accounting-assistant

# 重启服务
sudo systemctl restart accounting-assistant

# 查看日志
sudo journalctl -u accounting-assistant -n 100 --no-pager
```

## 反向代理配置

### Nginx

**安装 Nginx：**

```bash
# Ubuntu/Debian
sudo apt install -y nginx

# CentOS/RHEL
sudo yum install -y nginx
```

**配置文件** `/etc/nginx/sites-available/accounting-assistant`：

```nginx
upstream accounting_backend {
    server localhost:8081;
}

server {
    listen 80;
    server_name your-domain.com;

    # 重定向到 HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    # SSL 证书
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    # SSL 配置
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # 安全头
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # 至少 6 MiB：图片最多 5 MiB，multipart 边界还需要额外空间
    client_max_body_size 10M;

    # 日志
    access_log /var/log/nginx/accounting-assistant-access.log;
    error_log /var/log/nginx/accounting-assistant-error.log;

    location / {
        proxy_pass http://accounting_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket 支持（如需要）
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        # 超时设置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 180s;
    }
}
```

**启用配置：**

```bash
sudo ln -s /etc/nginx/sites-available/accounting-assistant /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### Caddy

**安装 Caddy：**

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install caddy
```

**配置文件** `/etc/caddy/Caddyfile`：

```caddy
your-domain.com {
    reverse_proxy localhost:8081
    encode gzip
}
```

**启动服务：**

```bash
sudo systemctl reload caddy
```

## 备份和恢复

### 手动备份

```bash
# 停止应用
sudo systemctl stop accounting-assistant

# 备份数据库文件
cp /opt/accounting-assistant/accounting-assistant.db \
   /opt/accounting-assistant/backups/accounting-assistant-$(date +%Y%m%d-%H%M%S).db

# 启动应用
sudo systemctl start accounting-assistant
```

### 自动备份脚本

创建 `/opt/accounting-assistant/backup.sh`：

```bash
#!/bin/bash

BACKUP_DIR="/opt/accounting-assistant/backups"
DB_FILE="/opt/accounting-assistant/accounting-assistant.db"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="$BACKUP_DIR/accounting-assistant-$TIMESTAMP.db"

# 创建备份目录
mkdir -p "$BACKUP_DIR"

# 备份数据库
cp "$DB_FILE" "$BACKUP_FILE"

# 压缩备份
gzip "$BACKUP_FILE"

# 删除 30 天前的备份
find "$BACKUP_DIR" -name "*.db.gz" -mtime +30 -delete

echo "Backup completed: $BACKUP_FILE.gz"
```

**设置定时任务：**

```bash
# 添加到 crontab（每天凌晨 2 点备份）
sudo crontab -e

# 添加以下行
0 2 * * * /opt/accounting-assistant/backup.sh >> /var/log/accounting-backup.log 2>&1
```

### 恢复备份

```bash
# 停止应用
sudo systemctl stop accounting-assistant

# 恢复数据库
cp /opt/accounting-assistant/backups/accounting-assistant-20260116-020000.db \
   /opt/accounting-assistant/accounting-assistant.db

# 启动应用
sudo systemctl start accounting-assistant
```

## 监控和日志

### 查看应用日志

**systemd 服务：**

```bash
# 实时日志
sudo journalctl -u accounting-assistant -f

# 最近 100 条
sudo journalctl -u accounting-assistant -n 100

# 查看错误
sudo journalctl -u accounting-assistant -p err
```

**Docker：**

```bash
docker logs -f accounting-assistant
```

### 健康检查

```bash
# 检查应用是否运行
curl http://localhost:8081/api/auth/csrf

# 检查磁盘空间
df -h /opt/accounting-assistant

# 检查内存使用
free -h
```

## 性能优化

### JVM 参数优化

根据服务器内存调整：

```ini
# 2GB 内存服务器
Environment="JAVA_OPTS=-Xmx1024m -Xms512m -XX:+UseG1GC"

# 4GB 内存服务器
Environment="JAVA_OPTS=-Xmx2048m -Xms1024m -XX:+UseG1GC"
```

### SQLite 优化

启用 WAL 模式（提升并发性能）：

```bash
sqlite3 accounting-assistant.db "PRAGMA journal_mode=WAL;"
```

### Nginx 缓存（可选）

```nginx
location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2)$ {
    proxy_pass http://accounting_backend;
    expires 7d;
    add_header Cache-Control "public, immutable";
}
```

## 安全建议

1. **使用 HTTPS**
2. **定期更新依赖和系统**
3. **限制文件权限**：

```bash
sudo chmod 600 /opt/accounting-assistant/accounting-assistant.db
sudo chown accounting:accounting /opt/accounting-assistant/accounting-assistant.db
```

4. **配置防火墙**：

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

5. **使用强密钥**：

```bash
# 生成 Remember-me 密钥
openssl rand -hex 32
```

## 故障排查

### 应用无法启动

1. 检查日志：`sudo journalctl -u accounting-assistant -n 100`
2. 检查 Java 版本：`java -version`
3. 检查端口占用：`sudo lsof -i :8081`

### 数据库锁定

```bash
# 检查 WAL 模式
sqlite3 accounting-assistant.db "PRAGMA journal_mode;"

# 启用 WAL
sqlite3 accounting-assistant.db "PRAGMA journal_mode=WAL;"
```

### 内存不足

```bash
# 查看内存使用
free -h

# 调整 JVM 参数
Environment="JAVA_OPTS=-Xmx512m -Xms256m"
```

### 截图入口不显示或图片识别失败

1. 检查已运行进程的配置是否启用 `AI_MULTIMODAL_ENABLED`，并确认 Compose 修改 `.env` 后已重新创建容器；本地运行请配置进程环境或 `application-local.yml`。
2. 确认配置的模型和代理支持 OpenAI 兼容图片输入。文字解析成功并不能证明图片也受支持。
3. 检查图片是否完整、是否为 PNG/JPEG，以及文件是否超过 5 MiB、尺寸是否超过 2000 万像素；超过 20 笔请裁剪后分次识别。
4. HTTP 413 通常与应用或代理上传上限有关，检查每一层代理至少允许 6 MiB。HTTP 504 则需检查模型响应时间及代理读取超时。
5. 若返回配额耗尽，等待下一日或按部署需求调整 `app.ai.daily-limit`；图片与文字解析共用此限额。

## 支持

如有问题，请通过以下方式获取帮助：

- [GitHub Issues](https://github.com/liuqitoday/smart-accounting-assistant/issues)
- [GitHub Discussions](https://github.com/liuqitoday/smart-accounting-assistant/discussions)
