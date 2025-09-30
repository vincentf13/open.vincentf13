FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/market-data-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Dserver.address=0.0.0.0","-jar","/app/app.jar"]
