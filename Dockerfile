# Compila bytecode Java 18 con un JDK LTS aislado de la computadora local.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends libstdc++6 fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system store && useradd --system --gid store --home-dir /app store
WORKDIR /app
COPY --from=build --chown=store:store /build/target/procesos-0.0.1-SNAPSHOT.jar app.jar
USER store
ENV SPRING_PROFILES_ACTIVE=cloud
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
