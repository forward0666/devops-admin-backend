import httpx
import logging
import os
import re

from app.config import (
    NACOS_HOST, NACOS_PORT, NACOS_NAMESPACE,
    NACOS_USERNAME, NACOS_PASSWORD, NACOS_GROUP,
    SERVICE_NAME,
)

logger = logging.getLogger(__name__)

NACOS_URL = f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1"

# Simple key -> (config attr, type)
CONFIG_MAP = {
    "service.name": ("SERVICE_NAME", str),
    "service.port": ("SERVICE_PORT", int),
    "service.ip": ("SERVICE_IP", str),
    "cf.base-url": ("CF_BASE_URL", str),
    "mysql.host": ("MYSQL_HOST", str),
    "mysql.port": ("MYSQL_PORT", int),
    "mysql.user": ("MYSQL_USER", str),
    "mysql.password": ("MYSQL_PASSWORD", str),
    "mysql.database": ("MYSQL_DATABASE", str),
    "redis.host": ("REDIS_HOST", str),
    "redis.port": ("REDIS_PORT", int),
    "redis.password": ("REDIS_PASSWORD", str),
    "redis.database": ("REDIS_DATABASE", int),
    "mongodb.host": ("MONGODB_HOST", str),
    "mongodb.port": ("MONGODB_PORT", int),
    "mongodb.user": ("MONGODB_USER", str),
    "mongodb.password": ("MONGODB_PASSWORD", str),
    "mongodb.database": ("MONGODB_DATABASE", str),
    "mongodb.auth-db": ("MONGODB_AUTH_DB", str),
}


def _resolve(value: str) -> str:
    """Resolve ${ENV_VAR:default} -> env value or default"""
    match = re.match(r"^\$\{(.+):(.+)\}$", value)
    if match:
        env_val = os.getenv(match.group(1))
        return env_val if env_val is not None else match.group(2)
    return value


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
                        resolved = _resolve(value.strip())
                        setattr(config, attr, cast(resolved))

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


async def send_heartbeat():
    """Send heartbeat to Nacos to keep instance alive"""
    from app.config import SERVICE_PORT, SERVICE_IP
    import json, time

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
            if resp.status_code == 200:
                logger.debug("💓 Nacos heartbeat sent")
            else:
                logger.warning(f"⚠️ Nacos heartbeat failed: {resp.status_code}")
    except Exception as e:
        logger.warning(f"⚠️ Nacos heartbeat error: {e}")
