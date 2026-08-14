FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY backend/pom.xml .
COPY backend/src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=build /app/target/nexus-backend-1.0.0.jar app.jar

COPY worker /app/worker

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]