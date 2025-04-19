FROM eclipse-temurin:17-jdk

RUN apt-get update && \
    apt-get install -y ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

CMD ["java", "-Dserver.port=8080", "-jar", "/app/app.jar"]
