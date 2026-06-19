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

CONFIG_MAP = {
    "service.name": ("SERVICE_NAME", str),
    "service.port": ("SERVICE_PORT", int),
    "mongodb.host": ("MONGODB_HOST", str),
    "mongodb.port": ("MONGODB_PORT", int),
    "mongodb.user": ("MONGODB_USER", str),
    "mongodb.password": ("MONGODB_PASSWORD", str),
    "mongodb.database": ("MONGODB_DATABASE", str),
    "mongodb.auth-db": ("MONGODB_AUTH_DB", str),
    "cloudflare.source.mongodb.database": ("CLOUDFLARE_MONGODB_DATABASE", str),
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
        "dataId": "domain.properties",
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
                        resolved = _resolve(value.strip().split("#")[0].strip(), cast)
                        setattr(config, attr, cast(resolved))
                logger.info("✅ Loaded config from Nacos: domain.properties")
            else:
                logger.warning("⚠️ Nacos config not found, using defaults")
    except Exception as e:
        logger.warning(f"⚠️ Failed to fetch Nacos config: {e}")


async def register_service():
    from app.config import SERVICE_PORT

    params = {
        "serviceName": SERVICE_NAME,
        "ip": os.getenv("SERVICE_IP") or os.getenv("POD_IP", "127.0.0.1"),
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
                logger.info(f"✅ Registered to Nacos: {SERVICE_NAME}")
            else:
                logger.error(f"❌ Nacos registration failed: {resp.status_code} {resp.text}")
    except Exception as e:
        logger.error(f"❌ Nacos registration failed: {e}")


async def send_heartbeat():
    from app.config import SERVICE_PORT
    import json

    ip = os.getenv("SERVICE_IP") or os.getenv("POD_IP", "127.0.0.1")
    beat = json.dumps({"ip": ip, "port": SERVICE_PORT, "serviceName": SERVICE_NAME})
    params = {
        "serviceName": SERVICE_NAME,
        "ip": ip,
        "port": SERVICE_PORT,
        "namespaceId": NACOS_NAMESPACE,
        "beat": beat,
        "username": NACOS_USERNAME,
        "password": NACOS_PASSWORD,
    }
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            await client.put(f"{NACOS_URL}/ns/instance/beat", params=params)
    except Exception:
        pass
