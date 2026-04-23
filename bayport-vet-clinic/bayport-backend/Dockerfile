# Build and run Bayport API on Render (Java 17 + Spring Boot)
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/bayport-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# Supabase PostgreSQL + Render PORT (override profile locally if needed)
ENV SPRING_PROFILES_ACTIVE=freecloud
# Render ↔ Supabase: avoid IPv6 "Network is unreachable" on direct db.* hostnames
# Still use Session pooler JDBC in SPRING_DATASOURCE_URL (see CLOUD_SETUP.md).
# Render sets PORT; application-freecloud uses server.port=${PORT:8080}
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]
