FROM eclipse-temurin:25-jre-noble

WORKDIR /app
COPY server/target/server-1.0-SNAPSHOT.jar shooter-server.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "shooter-server.jar"]
