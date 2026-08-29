FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src/main ./src/main

RUN ./gradlew clean bootJar -x test --no-daemon \
	&& BOOT_JAR="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
	&& test -n "$BOOT_JAR" \
	&& cp "$BOOT_JAR" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system spring \
	&& useradd --system --gid spring --home-dir /app --shell /usr/sbin/nologin spring \
	&& mkdir -p /app/storage \
	&& chown -R spring:spring /app

COPY --from=builder --chown=spring:spring /workspace/app.jar /app/app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
