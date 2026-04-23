# Build and run Bayport API on Render (Java 17 + Spring Boot)
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/bayport-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# Render sets PORT; application-freecloud uses server.port=${PORT:8080}
ENTRYPOINT ["java", "-jar", "app.jar"]
