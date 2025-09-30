FROM eclipse-temurin:24-jdk-alpine
WORKDIR /app
COPY target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Dserver.address=0.0.0.0","-Dserver.port=8080","-jar","/app/app.jar"]
