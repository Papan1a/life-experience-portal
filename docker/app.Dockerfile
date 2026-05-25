# docker/app.Dockerfile
# Multi-stage build per AI_IMPLEMENTATION_GUIDE.md Block 0.5.
# Stage 1: build with Maven wrapper; Stage 2: run on JRE 21.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
