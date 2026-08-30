# Build the fat jar with a full JDK, then throw the JDK away and ship only a JRE
# plus the jar. Keeps the runtime image small and leaves no compiler or source in it.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY . .
RUN chmod +x gradlew && ./gradlew :app:bootJar --no-daemon

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# The app has no reason to be root, and a container escape is worth less without it.
RUN groupadd --system lynk && useradd --system --gid lynk lynk

COPY --from=build /workspace/app/build/libs/*.jar app.jar
USER lynk

# Documentation only. The host decides the real port and passes it as PORT.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]