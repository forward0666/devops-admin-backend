FROM amazoncorretto:21.0.9-alpine3.22
WORKDIR /app
RUN adduser --disabled-password --gecos "" appuser
ENV TZ=Asia/Shanghai
ARG JAR_FILE=security/target/security-1.0.0.jar
COPY ${JAR_FILE} app.jar
RUN chown -R appuser:appuser /app
USER appuser
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar" ]
EXPOSE 8082
