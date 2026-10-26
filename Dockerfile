FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/ledgerline-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 ledgerline
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER ledgerline
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
