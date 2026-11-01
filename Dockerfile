# 110 告警系统运行镜像：基座取自集群私有仓库（外网拉取过慢），jar 用 Spring Boot fat jar
FROM registry.galaxy.local:30500/base/eclipse-temurin:17-jre
ARG JAR_FILE
ARG APP_PORT=8080
ENV APP_PORT=${APP_PORT}
ENV JAVA_OPTS=""
WORKDIR /app
COPY ${JAR_FILE} app.jar
EXPOSE ${APP_PORT}
ENTRYPOINT ["sh","-c","exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75 ${JAVA_OPTS:-} -jar /app/app.jar"]
