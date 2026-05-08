import nacos
import os
import uvicorn
import threading

NACOS_HOST = os.getenv("NACOS_HOST", "192.168.86.9")
NACOS_PORT = int(os.getenv("NACOS_PORT", "8848"))
NACOS_NAMESPACE = os.getenv("NACOS_NAMESPACE", "6c5b1db3-a808-4543-a87e-6642e372cb4f")
NACOS_USERNAME = os.getenv("NACOS_USERNAME", "nacos")
NACOS_PASSWORD = os.getenv("NACOS_PASSWORD", "nacos")
SERVICE_NAME = "cloudflare"
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8090"))
SERVICE_IP = os.getenv("SERVICE_IP", "127.0.0.1")


def register_service():
    client = nacos.NacosClient(
        f"{NACOS_HOST}:{NACOS_PORT}",
        namespace=NACOS_NAMESPACE,
        username=NACOS_USERNAME,
        password=NACOS_PASSWORD,
    )
    client.add_naming_instance(
        SERVICE_NAME,
        SERVICE_IP,
        SERVICE_PORT,
        healthy=True,
        enabled=True,
        weight=1.0,
        metadata={"version": "1.0.0", "type": "python"},
    )
    print(f"✅ Registered to Nacos: {SERVICE_NAME} ({SERVICE_IP}:{SERVICE_PORT})")
    return client


def main():
    nacos_client = register_service()
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=SERVICE_PORT,
        log_level="info",
    )


if __name__ == "__main__":
    main()
