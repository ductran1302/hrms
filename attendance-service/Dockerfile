FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
COPY . .
RUN mvn -B -pl attendance-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /src/attendance-service/target/attendance-service-*.jar app.jar
COPY --from=build /src/THIRD-PARTY-NOTICES.md /app/THIRD-PARTY-NOTICES.md
COPY --from=build /src/THIRD-PARTY-BACKEND.txt /app/THIRD-PARTY-BACKEND.txt
COPY --from=build /src/LICENSES /app/LICENSES
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
