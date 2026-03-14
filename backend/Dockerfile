# 后端构建阶段
FROM maven:3.8-openjdk-8-slim AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖（利用 Docker 缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN mvn package -DskipTests -B

# 运行阶段
FROM openjdk:8-jre-slim

WORKDIR /app

# 安装必要的工具
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 从构建阶段复制 jar 文件
COPY --from=builder /app/target/*.jar app.jar

# 创建非 root 用户
RUN groupadd -r fieldcheck && useradd -r -g fieldcheck fieldcheck
RUN mkdir -p /app/logs /app/reports && chown -R fieldcheck:fieldcheck /app
USER fieldcheck

# 暴露端口
EXPOSE 8080

# JVM 参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseConcMarkSweepGC"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
