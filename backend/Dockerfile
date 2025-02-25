FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

CMD ["java", "-Dserver.port=8080", "-jar", "/app/app.jar"]
