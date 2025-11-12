FROM alibabadragonwell/dragonwell:21-alpine3.18
WORKDIR /app
RUN adduser --disabled-password --gecos "" appuser
ARG JAR_FILE=cloudflare-logs-producer/target/cloudflare-logs-producer-1.0.0.jar
COPY ${JAR_FILE} app.jar
RUN sh -c 'touch app.jar'
ENV TZ=Asia/Shanghai JAVA_OPTS="-Dspring.config.location=classpath:/application.properties -Xms200m -Xmx4096m"
RUN chown -R appuser:appuser /app
USER appuser
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar" ]
EXPOSE 8084

