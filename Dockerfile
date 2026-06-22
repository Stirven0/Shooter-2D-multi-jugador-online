FROM eclipse-temurin:25-jre-noble

WORKDIR /app
COPY server/target/server.jar shooter-server.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "shooter-server.jar"]
