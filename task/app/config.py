import os
import re


def _parse_port(env_val: str, default: str) -> int:
    val = os.getenv(env_val, default)
    if not val:
        return int(default)
    match = re.search(r':(\d+)/?$', val)
    if match:
        return int(match.group(1))
    try:
        return int(val)
    except ValueError:
        return int(default)


NACOS_HOST = os.getenv("NACOS_HOST", "192.168.86.9")
NACOS_PORT = _parse_port("NACOS_PORT", "8848")
NACOS_NAMESPACE = os.getenv("NACOS_NAMESPACE", "6c5b1db3-a808-4543-a87e-6642e372cb4f")
NACOS_USERNAME = os.getenv("NACOS_USERNAME", "nacos")
NACOS_PASSWORD = os.getenv("NACOS_PASSWORD", "nacos")
NACOS_GROUP = os.getenv("NACOS_GROUP", "DEFAULT_GROUP")

SERVICE_NAME = "task"
SERVICE_PORT = _parse_port("SERVICE_PORT", "8092")
SERVICE_IP = os.getenv("SERVICE_IP") or os.getenv("POD_IP", "127.0.0.1")

# MySQL
MYSQL_HOST = os.getenv("MYSQL_HOST", "192.168.86.9")
MYSQL_PORT = _parse_port("MYSQL_PORT", "3306")
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "task")

# MongoDB
MONGODB_HOST = os.getenv("MONGODB_HOST", "192.168.86.9")
MONGODB_PORT = _parse_port("MONGODB_PORT", "27017")
MONGODB_USER = os.getenv("MONGODB_USERNAME", "root")
MONGODB_PASSWORD = os.getenv("MONGODB_PASSWORD", "root123")
MONGODB_DATABASE = os.getenv("MONGODB_DATABASE", "task")
MONGODB_AUTH_DB = os.getenv("MONGODB_AUTH_DB", "admin")

# Redis
REDIS_HOST = os.getenv("REDIS_HOST", "192.168.86.9")
REDIS_PORT = _parse_port("REDIS_PORT", "6379")
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "root123")
REDIS_DATABASE = _parse_port("REDIS_DATABASE", "0")

# Service-to-service auth
GATEWAY_SECRET = os.getenv("GATEWAY_SECRET", "")
