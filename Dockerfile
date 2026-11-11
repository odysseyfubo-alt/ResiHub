# =====================================================================
# ResiHub 镜像：多阶段构建（Maven 编译 → JRE 运行）
# 构建：docker build -t resihub:1.0.0 .
# 说明：运行阶段只需要 JDK 21（编译期 Lombok 1.18.34 需要 16+）
# =====================================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷 pom，利用依赖层缓存，改代码时不用重新下依赖
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8"
WORKDIR /app

COPY --from=build /build/target/resihub-*.jar /app/app.jar

EXPOSE 8082
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
