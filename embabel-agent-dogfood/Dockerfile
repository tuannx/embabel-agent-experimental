# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21-alpine AS builder

WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress \
    -pl embabel-agent-dogfood -am \
    -DskipTests -Dmaven.gitcommitid.skip=true package

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache git github-cli \
    && addgroup -S -g 10001 embabel \
    && adduser -S -D -u 10001 -G embabel embabel \
    && mkdir -p /workspace/repo /output /home/embabel/.config/gh \
    && chown -R embabel:embabel /workspace /output /home/embabel

ENV HOME=/home/embabel

COPY --from=builder --chown=embabel:embabel \
    /build/embabel-agent-dogfood/target/embabel-agent-dogfood-app.jar \
    /app/embabel-agent-dogfood.jar

USER 10001:10001
WORKDIR /workspace/repo

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.io.tmpdir=/tmp", "-jar", "/app/embabel-agent-dogfood.jar"]
