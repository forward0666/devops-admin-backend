FROM python:3.14-alpine
WORKDIR /app
RUN adduser --disabled-password --gecos "" appuser
ENV TZ=Asia/Shanghai
RUN chown -R appuser:appuser /app
RUN pip install -r requirements.txt
USER appuser
ENTRYPOINT [ "sh", "-c", "python run.py " ]
EXPOSE 8089 11111