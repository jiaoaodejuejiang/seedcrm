FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /workspace/target/*.jar /app/seedcrm-server.jar
EXPOSE 8004
ENTRYPOINT ["java", "-jar", "/app/seedcrm-server.jar"]
