import httpx
import logging

from app.config import (
    NACOS_HOST, NACOS_PORT, NACOS_NAMESPACE,
    NACOS_USERNAME, NACOS_PASSWORD, NACOS_GROUP,
    SERVICE_NAME,
)

logger = logging.getLogger(__name__)

NACOS_URL = f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1"

# Extract value from Spring-style ${VAR:default} syntax
def _resolve(value: str) -> str:
    import re
    match = re.match(r"^\$\{(.+):(.+)\}$", value)
    if match:
        return match.group(2)  # return default
    return value

# Extract host/port from JDBC URL: jdbc:mysql://host:port/db
def _parse_jdbc_url(url: str) -> tuple:
    import re
    match = re.search(r"jdbc:mysql://([^:]+):(\d+)", url)
    if match:
        return match.group(1), int(match.group(2))
    return url, 3306

async def fetch_config():
    """Pull config from Nacos and override local defaults"""
    import app.config as config
    params = {
        "dataId": "cloudflare.properties",
        "group": NACOS_GROUP,
        "tenant": NACOS_NAMESPACE,
        "username": NACOS_USERNAME,
        "password": NACOS_PASSWORD,
    }
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(f"{NACOS_URL}/cs/configs", params=params)
            if resp.status_code == 200 and resp.text:
                raw = {}
                for line in resp.text.strip().split("\n"):
                    line = line.strip()
                    if not line or line.startswith("#"):
                        continue
                    if "=" not in line:
                        continue
                    key, value = line.split("=", 1)
                    raw[key.strip()] = value.strip()

                # Service
                if "service.name" in raw:
                    config.SERVICE_NAME = raw["service.name"]
                if "service.port" in raw:
                    config.SERVICE_PORT = int(_resolve(raw["service.port"]))
                if "service.ip" in raw:
                    config.SERVICE_IP = raw["service.ip"]
                if "cf.base-url" in raw:
                    config.CF_BASE_URL = raw["cf.base-url"]

                # MySQL - parse from JDBC URL
                if "spring.datasource.url" in raw:
                    host, port = _parse_jdbc_url(raw["spring.datasource.url"])
                    config.MYSQL_HOST = host
                    config.MYSQL_PORT = port
                if "spring.datasource.username" in raw:
                    config.MYSQL_USER = _resolve(raw["spring.datasource.username"])
                if "spring.datasource.password" in raw:
                    config.MYSQL_PASSWORD = _resolve(raw["spring.datasource.password"])
                if "spring.datasource.url" in raw:
                    import re
                    db_match = re.search(r"/(\w+)\?", raw["spring.datasource.url"])
                    if db_match:
                        config.MYSQL_DATABASE = db_match.group(1)

                # Redis
                if "spring.data.redis.host" in raw:
                    config.REDIS_HOST = _resolve(raw["spring.data.redis.host"])
                if "spring.data.redis.port" in raw:
                    config.REDIS_PORT = int(_resolve(raw["spring.data.redis.port"]))
                if "spring.data.redis.password" in raw:
                    config.REDIS_PASSWORD = _resolve(raw["spring.data.redis.password"])
                if "spring.data.redis.database" in raw:
                    config.REDIS_DATABASE = int(raw["spring.data.redis.database"])

                # MongoDB
                if "spring.data.mongodb.host" in raw:
                    config.MONGODB_HOST = _resolve(raw["spring.data.mongodb.host"])
                if "spring.data.mongodb.port" in raw:
                    config.MONGODB_PORT = int(_resolve(raw["spring.data.mongodb.port"]))
                if "spring.data.mongodb.username" in raw:
                    config.MONGODB_USER = _resolve(raw["spring.data.mongodb.username"])
                if "spring.data.mongodb.password" in raw:
                    config.MONGODB_PASSWORD = _resolve(raw["spring.data.mongodb.password"])
                if "spring.data.mongodb.database" in raw:
                    config.MONGODB_DATABASE = _resolve(raw["spring.data.mongodb.database"])
                if "spring.data.mongodb.authentication-database" in raw:
                    config.MONGODB_AUTH_DB = raw["spring.data.mongodb.authentication-database"]

                logger.info("✅ Loaded config from Nacos: cloudflare.properties")
            else:
                logger.warning("⚠️ Nacos config not found, using defaults")
    except Exception as e:
        logger.warning(f"⚠️ Failed to fetch Nacos config: {e}")


async def register_service():
    """Register service to Nacos"""
    from app.config import SERVICE_PORT, SERVICE_IP

    params = {
        "serviceName": SERVICE_NAME,
        "ip": SERVICE_IP,
        "port": SERVICE_PORT,
        "enabled": "true",
        "healthy": "true",
        "weight": 1.0,
        "metadata": '{"version":"1.0.0","type":"python"}',
        "namespaceId": NACOS_NAMESPACE,
        "username": NACOS_USERNAME,
        "password": NACOS_PASSWORD,
    }
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(f"{NACOS_URL}/ns/instance", params=params)
            if resp.status_code == 200 and "ok" in resp.text:
                logger.info(f"✅ Registered to Nacos: {SERVICE_NAME} ({SERVICE_IP}:{SERVICE_PORT})")
            else:
                logger.error(f"❌ Nacos registration failed: {resp.status_code} {resp.text}")
    except Exception as e:
        logger.error(f"❌ Nacos registration failed: {e}")
