# syntax=docker/dockerfile:1

# Backend jar only. Infra (MySQL/Redis) stays in compose; Vite frontend stays on the host.
#
#   docker compose up -d --wait
#   docker compose --profile app up -d --build app
#   npm run dev:frontend   # http://127.0.0.1:5173 → proxies /api to :8001
#
# Dev / local sample only — not a production all-in-one image.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN cp -f .mvn/settings.xml.example .mvn/settings.xml \
  && mvn -B -pl chengwenfang-app -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --uid 10001 --create-home chengwenfang
# finalName is fixed in chengwenfang-app/pom.xml (version bumps do not rename the jar)
COPY --from=build /src/chengwenfang-app/target/chengwenfang-app.jar /app/app.jar
RUN chown chengwenfang:chengwenfang /app/app.jar

USER chengwenfang
EXPOSE 8001

ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
