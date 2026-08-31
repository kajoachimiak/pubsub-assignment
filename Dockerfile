FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Copy only the files needed to resolve dependencies first. This layer is cached
# and only re-run when the wrapper or build/settings scripts change, so editing
# application source code does not force Gradle to re-download the world.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies || true

# Now copy the source and build. Only this layer (and the final jar) is
# invalidated when application code changes.
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV PORT=8080

# Run as a dedicated, unprivileged user instead of root.
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown spring:spring app.jar

USER spring:spring

EXPOSE 8080

# Shell form (via "sh -c") so ${PORT} is expanded by the shell before the JVM
# starts; "exec" replaces the shell process with java so it becomes PID 1 and
# receives SIGTERM directly for graceful shutdown.
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT} -jar app.jar"]