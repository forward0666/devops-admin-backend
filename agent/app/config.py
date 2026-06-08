import os

NACOS_HOST = os.getenv("NACOS_HOST", "192.168.86.9")
NACOS_PORT = int(os.getenv("NACOS_PORT", "8848"))
NACOS_NAMESPACE = os.getenv("NACOS_NAMESPACE", "6c5b1db3-a808-4543-a87e-6642e372cb4f")
NACOS_USERNAME = os.getenv("NACOS_USERNAME", "nacos")
NACOS_PASSWORD = os.getenv("NACOS_PASSWORD", "nacos")

SERVICE_NAME = "agent"
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8094"))
SERVICE_IP = os.getenv("SERVICE_IP") or os.getenv("POD_IP") or ""

MYSQL_HOST = os.getenv("MYSQL_HOST", "192.168.86.9")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "agent")
MYSQL_POOL_RECYCLE = int(os.getenv("MYSQL_POOL_RECYCLE", "1800"))
