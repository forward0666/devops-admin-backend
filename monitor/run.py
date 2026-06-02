import asyncio
import logging
import uvicorn

from app.config import SERVICE_PORT
from app.services.nacos_client import fetch_config, register_service

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)


def main():
    # 1. Pull config from Nacos (overrides defaults)
    asyncio.run(fetch_config())

    # 2. Register service to Nacos (uses possibly overridden values)
    asyncio.run(register_service())

    # 3. Start FastAPI server
    from app.config import SERVICE_PORT  # re-read after config override
    logger.info(f"🚀 Starting Monitor Service on port {SERVICE_PORT}")
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=SERVICE_PORT,
        log_level="info",
    )


if __name__ == "__main__":
    main()
