import httpx
import logging
import logging.config
import os
import re

from app.config import (
    NACOS_HOST, NACOS_PORT, NACOS_NAMESPACE,
    NACOS_USERNAME, NACOS_PASSWORD, NACOS_GROUP,
    SERVICE_NAME,
)

logger = logging.getLogger(__name__)

LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {"default": {"format": "%(asctime)s %(levelname)s %(message)s"}},
    "handlers": {"default": {"class": "logging.StreamHandler", "formatter": "default"}},
    "loggers": {
        "httpx": {"level": logging.WARNING},
        "httpcore": {"level": logging.WARNING},
        "": {"handlers": ["default"], "level": logging.INFO},
    },
}
logging.config.dictConfig(LOGGING)

NACOS_URL = f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1"

CONFIG_MAP = {
    "service.name": ("SERVICE_NAME", str),
    "service.port": ("SERVICE_PORT", int),
    "service.ip": ("SERVICE_IP", str),
    "mysql.host": ("MYSQL_HOST", str),
    "mysql.port": ("MYSQL_PORT", int),
    "mysql.user": ("MYSQL_USER", str),
    "mysql.password": ("MYSQL_PASSWORD", str),
    "mysql.database": ("MYSQL_DATABASE", str),
    "mongodb.host": ("MONGODB_HOST", str),
    "mongodb.port": ("MONGODB_PORT", int),
    "mongodb.user": ("MONGODB_USER", str),
    "mongodb.password": ("MONGODB_PASSWORD", str),
    "mongodb.database": ("MONGODB_DATABASE", str),
    "mongodb.auth-db": ("MONGODB_AUTH_DB", str),
    "gateway.secret": ("GATEWAY_SECRET", str),
    "cloudflare.service.url": ("CLOUDFLARE_SERVICE_URL", str),
    "http.concurrency": ("HTTP_CONCURRENCY", int),
    "dns.concurrency": ("DNS_CONCURRENCY", int),
}


def _resolve(value: str, cast=None) -> str:
    match = re.match(r"^\$\{(.+):(.+)\}$", value)
    if match:
        env_val = os.getenv(match.group(1))
        resolved = env_val if env_val is not None else match.group(2)
        if cast is int and resolved:
            port_match = re.search(r':(\d+)/?$', resolved)
            if port_match:
                return port_match.group(1)
        return resolved
    if cast is int and value:
        port_match = re.search(r':(\d+)/?$', value)
        if port_match:
            return port_match.group(1)
    return value


async def fetch_config():
    import app.config as config

    params = {
        "dataId": "monitor.properties",
        "group": NACOS_GROUP,
        "tenant": NACOS_NAMESPACE,
        "username": NACOS_USERNAME,
        "password": NACOS_PASSWORD,
    }
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(f"{NACOS_URL}/cs/configs", params=params)
            if resp.status_code == 200 and resp.text:
                for line in resp.text.strip().split("\n"):
                    line = line.strip()
                    if not line or line.startswith("#"):
                        continue
                    if "=" not in line:
                        continue
                    key, value = line.split("=", 1)
                    key = key.strip()
                    if key in CONFIG_MAP:
                        attr, cast = CONFIG_MAP[key]
                        resolved = _resolve(value.strip(), cast)
                        setattr(config, attr, cast(resolved))
                logger.info("✅ Loaded config from Nacos: monitor.properties")
                for attr in ['SERVICE_NAME', 'SERVICE_PORT', 'MYSQL_HOST', 'MYSQL_PORT', 'MYSQL_DATABASE', 'MONGODB_HOST', 'MONGODB_PORT', 'MONGODB_DATABASE', 'REDIS_HOST', 'REDIS_PORT']:
                    logger.info(f"  {attr} = {getattr(config, attr, 'NOT SET')}")
            else:
                logger.warning("⚠️ Nacos config not found, using defaults")
    except Exception as e:
        logger.warning(f"⚠️ Failed to fetch Nacos config: {e}")


async def register_service():
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


async def send_heartbeat():
    from app.config import SERVICE_PORT, SERVICE_IP
    import json

    beat = json.dumps({"ip": SERVICE_IP, "port": SERVICE_PORT, "serviceName": SERVICE_NAME})
    params = {
        "serviceName": SERVICE_NAME,
        "ip": SERVICE_IP,
        "port": SERVICE_PORT,
        "namespaceId": NACOS_NAMESPACE,
        "beat": beat,
        "username": NACOS_USERNAME,
        "password": NACOS_PASSWORD,
    }
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.put(f"{NACOS_URL}/ns/instance/beat", params=params)
            if resp.status_code != 200:
                logger.warning(f"⚠️ Nacos heartbeat failed: {resp.status_code}")
    except Exception as e:
        logger.warning(f"⚠️ Nacos heartbeat error: {e}")
