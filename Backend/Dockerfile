FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY build.gradle settings.gradle* ./
COPY gradle ./gradle
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 5000
ENTRYPOINT ["java", "-jar", "app.jar"]
