FROM amazoncorretto:21.0.9-alpine3.22
WORKDIR /app
RUN adduser --disabled-password --gecos "" appuser
ARG JAR_FILE=gateway/target/gateway-1.0.0.jar
COPY ${JAR_FILE} app.jar
#RUN sh -c 'touch app.jar'
ENV TZ=Asia/Shanghai JAVA_OPTS="-Dspring.config.location=classpath:/application.properties -Duser.timezone=Asia/Shanghai"
RUN chown -R appuser:appuser /app
USER appuser
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar" ]
EXPOSE 8081 11111