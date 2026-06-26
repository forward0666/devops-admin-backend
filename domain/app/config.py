import os

SERVICE_NAME = "domain"
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8091"))

# Nacos
NACOS_HOST = os.getenv("NACOS_HOST", "192.168.86.9")
NACOS_PORT = int(os.getenv("NACOS_PORT", "8848"))
NACOS_NAMESPACE = os.getenv("NACOS_NAMESPACE", "6c5b1db3-a808-4543-a87e-6642e372cb4f")
NACOS_USERNAME = os.getenv("NACOS_USERNAME", "nacos")
NACOS_PASSWORD = os.getenv("NACOS_PASSWORD", "nacos")
NACOS_GROUP = os.getenv("NACOS_GROUP", "DEFAULT_GROUP")

# MongoDB
MONGODB_HOST = os.getenv("MONGODB_HOST", "192.168.86.9")
MONGODB_PORT = int(os.getenv("MONGODB_PORT", "27017"))
MONGODB_USER = os.getenv("MONGODB_USERNAME", "root")
MONGODB_PASSWORD = os.getenv("MONGODB_PASSWORD", "root123")
MONGODB_DATABASE = os.getenv("MONGODB_DATABASE", "domain")
MONGODB_AUTH_DB = os.getenv("MONGODB_AUTH_DB", "admin")

# Source MongoDB
CLOUDFLARE_MONGODB_DATABASE = os.getenv("CLOUDFLARE_MONGODB_DATABASE", "cloudflare")

# Redis
REDIS_HOST = os.getenv("REDIS_HOST", "192.168.86.9")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "root123")
REDIS_DATABASE = int(os.getenv("REDIS_DATABASE", "0"))

# MySQL
MYSQL_HOST = os.getenv("MYSQL_HOST", "192.168.86.9")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "domain")
MYSQL_POOL_RECYCLE = int(os.getenv("MYSQL_POOL_RECYCLE", "1800"))
