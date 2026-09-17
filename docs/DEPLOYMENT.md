# 部署指南

本文档提供了详细的部署方案，包括 Docker、systemd、反向代理等。

## 目录

- [Docker 部署](#docker-部署)
- [Docker Compose 部署](#docker-compose-部署)
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
REMEMBER_ME_KEY=change-me-to-random-hex-string
COOKIE_SECURE=false
```

### 2. 启动服务

```bash
docker-compose up -d
```

### 3. 查看状态

```bash
# 查看运行状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 查看实时日志（特定服务）
docker-compose logs -f app
```

### 4. 停止服务

```bash
docker-compose down
```

### 5. 更新服务

```bash
# 拉取最新代码
git pull

# 重新构建并启动
docker-compose up -d --build
```

## 传统部署

### 1. 准备环境

**系统要求：**
- Linux 服务器（Ubuntu 20.04+ / CentOS 8+ 推荐）
- Java 17 或更高版本
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

    # 文件上传大小限制
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
        proxy_read_timeout 60s;
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

## 支持

如有问题，请通过以下方式获取帮助：

- [GitHub Issues](https://github.com/liuqitoday/smart-accounting-assistant/issues)
- [GitHub Discussions](https://github.com/liuqitoday/smart-accounting-assistant/discussions)
