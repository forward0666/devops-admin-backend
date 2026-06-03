import os

NACOS_HOST = os.getenv("NACOS_HOST", "192.168.86.9")
NACOS_PORT = int(os.getenv("NACOS_PORT", "8848"))
NACOS_NAMESPACE = os.getenv("NACOS_NAMESPACE", "6c5b1db3-a808-4543-a87e-6642e372cb4f")
NACOS_USERNAME = os.getenv("NACOS_USERNAME", "nacos")
NACOS_PASSWORD = os.getenv("NACOS_PASSWORD", "nacos")
NACOS_GROUP = os.getenv("NACOS_GROUP", "DEFAULT_GROUP")

# Defaults (will be overridden by Nacos config)
SERVICE_NAME = "cloudflare"
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8090"))
SERVICE_IP = os.getenv("SERVICE_IP", "127.0.0.1")
CF_BASE_URL = os.getenv("CF_BASE_URL", "https://api.cloudflare.com/client/v4")

# MySQL
MYSQL_HOST = os.getenv("MYSQL_HOST", "192.168.86.9")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "cloudflare")

# Redis
REDIS_HOST = os.getenv("REDIS_HOST", "192.168.86.9")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "root123")
REDIS_DATABASE = int(os.getenv("REDIS_DATABASE", "0"))

# MongoDB
MONGODB_HOST = os.getenv("MONGODB_HOST", "192.168.86.9")
MONGODB_PORT = int(os.getenv("MONGODB_PORT", "27017"))
MONGODB_USER = os.getenv("MONGODB_USERNAME", "root")
MONGODB_PASSWORD = os.getenv("MONGODB_PASSWORD", "root123")
MONGODB_DATABASE = os.getenv("MONGODB_DATABASE", "cloudflare")
MONGODB_AUTH_DB = os.getenv("MONGODB_AUTH_DB", "admin")

# Service-to-service auth
GATEWAY_SECRET = os.getenv("GATEWAY_SECRET", "")
USER_SERVICE_URL = os.getenv("USER_SERVICE_URL", "http://192.168.86.9:8084")








