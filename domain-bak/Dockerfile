FROM python:3.11-slim

WORKDIR /app

COPY domain/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY domain/ .

CMD ["python", "run.py"]
