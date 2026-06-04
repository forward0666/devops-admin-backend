FROM python:3.14-alpine
RUN apk add --no-cache jemalloc-dev
WORKDIR /app
RUN adduser --disabled-password --gecos "" appuser
ENV LD_PRELOAD=/usr/lib/libjemalloc.so.2
COPY task/ .
RUN pip install -r requirements.txt
ENV TZ=Asia/Shanghai
RUN chown -R appuser:appuser /app
USER appuser
ENTRYPOINT [ "sh", "-c", "python run.py " ]
EXPOSE 8092 11111
