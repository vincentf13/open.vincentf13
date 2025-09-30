FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/risk-margin-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Dserver.address=0.0.0.0","-Dserver.port=8080","-jar","/app/app.jar"]
