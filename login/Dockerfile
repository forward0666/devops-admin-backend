FROM amazoncorretto:21.0.9-alpine3.22
WORKDIR /app
RUN adduser --disabled-password --gecos "" appuser
RUN apk update && \
    apk add tzdata && \
    /bin/cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo 'Asia/Shanghai' >/etc/timezone \
    sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && apk add --update ttf-dejavu fontconfig && rm -rf /var/cache/apk/* && mkfontscale && mkfontdir && fc-cache
ARG JAR_FILE=security/target/security-1.0.0.jar
COPY ${JAR_FILE} app.jar
#RUN sh -c 'touch app.jar'
ENV TZ=Asia/Shanghai JAVA_OPTS="-Dspring.config.location=classpath:/application.properties"
RUN chown -R appuser:appuser /app
USER appuser
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar" ]
EXPOSE 8081 11111