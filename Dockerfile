# 多阶段构建
# Stage 1: 编译（需要 JDK + Maven + Node.js）
FROM maven:3.9-eclipse-temurin-17 AS builder

# 安装 Node.js 20（前端构建需要）
RUN apt-get update && \
    apt-get install -y curl gnupg && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build

# 先复制前端依赖描述文件，npm ci 可独立缓存
COPY frontend/package.json frontend/package-lock.json frontend/
RUN cd frontend && npm ci

# 复制全部源码
COPY . .

# 完整构建（含前端 Vite 打包 + Maven 依赖下载）
RUN mvn clean package -DskipTests -q

# Stage 2: 运行时（只需要 JRE）
FROM eclipse-temurin:17-jre

WORKDIR /app

# 复制构建产物
COPY --from=builder /build/target/accounting-assistant-0.0.1-SNAPSHOT.jar app.jar

# 数据持久化目录
VOLUME /app/data

EXPOSE 8081

# SQLite 数据文件默认存到挂载卷；保留 WAL/busy_timeout 参数，避免容器内退回默认 journal 模式
ENV SPRING_DATASOURCE_URL="jdbc:sqlite:/app/data/accounting-assistant.db?journal_mode=WAL&busy_timeout=5000&synchronous=NORMAL"

ENTRYPOINT ["java", "-jar", "app.jar"]
