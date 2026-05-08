import httpx
import logging
import os

from app.config import (
    NACOS_HOST, NACOS_PORT, NACOS_NAMESPACE,
    NACOS_USERNAME, NACOS_PASSWORD, NACOS_GROUP,
    SERVICE_NAME,
)

logger = logging.getLogger(__name__)

NACOS_URL = f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1"


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
                    value = value.strip()
                    mapping = {
                        "service.name": ("SERVICE_NAME", str),
                        "service.port": ("SERVICE_PORT", int),
                        "service.ip": ("SERVICE_IP", str),
                        "cf.base-url": ("CF_BASE_URL", str),
                    }
                    if key in mapping:
                        attr, cast = mapping[key]
                        setattr(config, attr, cast(value))
                logger.info(f"✅ Loaded config from Nacos: cloudflare.properties")
            else:
                logger.warning(f"⚠️ Nacos config not found, using defaults")
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
