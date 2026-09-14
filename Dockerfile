FROM eclipse-temurin:11-jre
WORKDIR /app
COPY target/*.jar /app/app.jar
EXPOSE 9527 8443
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
