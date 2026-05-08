import asyncio
import logging
import uvicorn

from app.config import SERVICE_PORT
from app.services.nacos_client import register_service

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)


def main():
    # Register to Nacos before starting server
    asyncio.run(register_service())

    logger.info(f"🚀 Starting Cloudflare Manager on port {SERVICE_PORT}")
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=SERVICE_PORT,
        log_level="info",
    )


if __name__ == "__main__":
    main()
