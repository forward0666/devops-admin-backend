FROM python:3.14-alpine
WORKDIR /app
RUN adduser --disabled-password --gecos "" appuser
#COPY . .
COPY monitor/ .
RUN pip install -r requirements.txt
ENV TZ=Asia/Shanghai
RUN chown -R appuser:appuser /app
USER appuser
ENTRYPOINT [ "sh", "-c", "python run.py " ]
EXPOSE 8091 11111