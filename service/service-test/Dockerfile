FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app
COPY target/service-test-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Dserver.address=0.0.0.0","-jar","/app/app.jar"]
