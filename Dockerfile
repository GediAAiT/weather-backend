FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/weather-backend-1.0.0.jar app.jar
ENV SERVER_ADDRESS=0.0.0.0
# Hosts like Render inject PORT; fall back to 8081 locally.
CMD ["sh", "-c", "SERVER_PORT=${PORT:-8081} java -jar app.jar"]
