import httpx
import logging
import json
import socket
import os

from app.config import NACOS_HOST, NACOS_PORT, NACOS_NAMESPACE, NACOS_USERNAME, NACOS_PASSWORD, SERVICE_NAME, SERVICE_PORT

logger = logging.getLogger(__name__)

NACOS_URL = f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1"


def _get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def _resolve(value, cast=None):
    import re
    match = re.match(r"^\$\{(.+):(.+)\}$", value)
    if match:
        env_val = os.getenv(match.group(1))
        resolved = env_val if env_val is not None else match.group(2)
        return cast(resolved) if cast else resolved
    return cast(value) if cast else value


async def fetch_config():
    import app.config as config
    params = {
        "dataId": "agent.properties",
        "group": "DEFAULT_GROUP",
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
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, value = line.split("=", 1)
                    key = key.strip()
                    value = value.strip().split("#")[0].strip()
                    if key == "service.name":
                        config.SERVICE_NAME = _resolve(value)
                    elif key == "service.port":
                        config.SERVICE_PORT = _resolve(value, int)
                    elif key == "service.ip":
                        config.SERVICE_IP = _resolve(value)
                    elif key == "mysql.host":
                        config.MYSQL_HOST = _resolve(value)
                    elif key == "mysql.port":
                        config.MYSQL_PORT = _resolve(value, int)
                    elif key == "mysql.user":
                        config.MYSQL_USER = _resolve(value)
                    elif key == "mysql.password":
                        config.MYSQL_PASSWORD = _resolve(value)
                    elif key == "mysql.database":
                        config.MYSQL_DATABASE = _resolve(value)
                logger.info("✅ Loaded config from Nacos: agent.properties")
    except Exception as e:
        logger.warning(f"⚠️ Nacos config fetch failed: {e}")


async def register_service():
    import app.config as config
    ip = config.SERVICE_IP or _get_local_ip()
    params = {
        "serviceName": config.SERVICE_NAME,
        "ip": ip,
        "port": config.SERVICE_PORT,
        "enabled": "true", "healthy": "true", "weight": 1.0,
        "metadata": '{"version":"1.0.0","type":"python"}',
        "namespaceId": NACOS_NAMESPACE,
        "username": NACOS_USERNAME, "password": NACOS_PASSWORD,
    }
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(f"{NACOS_URL}/ns/instance", params=params)
            if resp.status_code == 200 and "ok" in resp.text:
                logger.info(f"✅ Registered to Nacos: {config.SERVICE_NAME} ({ip}:{config.SERVICE_PORT})")
            else:
                logger.error(f"❌ Nacos registration failed: {resp.text}")
    except Exception as e:
        logger.error(f"❌ Nacos registration failed: {e}")


async def send_heartbeat():
    import app.config as config
    ip = config.SERVICE_IP or _get_local_ip()
    beat = json.dumps({"ip": ip, "port": config.SERVICE_PORT, "serviceName": config.SERVICE_NAME})
    params = {
        "serviceName": config.SERVICE_NAME, "ip": ip, "port": config.SERVICE_PORT,
        "namespaceId": NACOS_NAMESPACE, "beat": beat,
        "username": NACOS_USERNAME, "password": NACOS_PASSWORD,
    }
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            await client.put(f"{NACOS_URL}/ns/instance/beat", params=params)
    except Exception:
        pass
