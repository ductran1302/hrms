FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
COPY . .
RUN mvn -B -pl gateway-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /src/gateway-service/target/gateway-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
