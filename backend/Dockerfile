FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace
COPY pom.xml .
COPY src ./src

# 构建阶段只生成应用包，运行阶段不携带 Maven 和源码。
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=builder /workspace/target/devops-platform-server-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
