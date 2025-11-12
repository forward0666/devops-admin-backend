FROM alibabadragonwell/dragonwell:21.0.8.0.8.9
WORKDIR /app
RUN adduser -D -h /app/appuser appuser
ARG JAR_FILE=cloudflare-logs-producer/target/cloudflare-logs-producer-1.0.0.jar
COPY ${JAR_FILE} app.jar
RUN sh -c 'touch app.jar'
ENV TZ=Asia/Shanghai
RUN chown -R appuser:appuser /app
USER appuser
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar" ]
EXPOSE 8084 11111