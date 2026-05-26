FROM maven:3.9.6-amazoncorretto-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM amazoncorretto:17-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE ${PORT:-8080}
CMD ["java", "-Xms32m", "-Xmx180m", "-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=160m", "-XX:CompressedClassSpaceSize=64m", "-XX:ReservedCodeCacheSize=32m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
