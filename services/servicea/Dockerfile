FROM eclipse-temurin:24-jdk-alpine
WORKDIR /app
COPY target/servicea-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-Dserver.address=0.0.0.0","-Dserver.port=8081","-jar","/app/app.jar"]
