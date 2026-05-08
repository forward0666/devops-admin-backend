import httpx
import logging

from app.config import (
    NACOS_HOST, NACOS_PORT, NACOS_NAMESPACE,
    NACOS_USERNAME, NACOS_PASSWORD,
    SERVICE_NAME, SERVICE_PORT, SERVICE_IP,
)

logger = logging.getLogger(__name__)

NACOS_URL = f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1/ns"


async def register_service():
    """Register service to Nacos via REST API (no SDK needed)"""
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
            resp = await client.post(f"{NACOS_URL}/instance", params=params)
            if resp.status_code == 200 and "ok" in resp.text:
                logger.info(f"✅ Registered to Nacos: {SERVICE_NAME} ({SERVICE_IP}:{SERVICE_PORT})")
            else:
                logger.error(f"❌ Failed to register Nacos: {resp.status_code} {resp.text}")
    except Exception as e:
        logger.error(f"❌ Nacos registration failed: {e}")
