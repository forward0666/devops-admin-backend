import os

SERVICE_NAME = "domain"
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8091"))

# MongoDB
MONGODB_HOST = os.getenv("MONGODB_HOST", "192.168.86.9")
MONGODB_PORT = int(os.getenv("MONGODB_PORT", "27017"))
MONGODB_USER = os.getenv("MONGODB_USERNAME", "root")
MONGODB_PASSWORD = os.getenv("MONGODB_PASSWORD", "root123")
MONGODB_DATABASE = os.getenv("MONGODB_DATABASE", "cloudflare")
MONGODB_AUTH_DB = os.getenv("MONGODB_AUTH_DB", "admin")
