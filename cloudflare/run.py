import nacos
import uvicorn

from app.config import (
    NACOS_HOST, NACOS_PORT, NACOS_NAMESPACE,
    NACOS_USERNAME, NACOS_PASSWORD,
    SERVICE_NAME, SERVICE_PORT, SERVICE_IP,
)


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
    register_service()
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=SERVICE_PORT,
        log_level="info",
    )


if __name__ == "__main__":
    main()
