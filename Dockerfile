# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependency resolution separately from application compilation.
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:17-jre
WORKDIR /app

# OpenShift may replace this UID with an arbitrary non-root UID. The application
# only needs read access to the JAR and writes temporary files under /tmp.
COPY --from=build --chown=1001:0 /workspace/target/azure-document-intelligence-storage-pipeline-1.0.0.jar /app/application.jar

USER 1001
EXPOSE 8080

ENV SERVER_PORT=8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
