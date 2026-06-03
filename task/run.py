import asyncio
import logging
import os
import threading
import time
import uvicorn

from app.config import SERVICE_PORT
from app.services.nacos_client import fetch_config, register_service, send_heartbeat

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)


def heartbeat_daemon():
    """Run in background thread, only from main process"""
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    while True:
        try:
            loop.run_until_complete(send_heartbeat())
        except Exception:
            pass
        time.sleep(5)


def main():
    asyncio.run(fetch_config())
    asyncio.run(register_service())

    from app.config import SERVICE_PORT
    workers = int(os.getenv("UVICORN_WORKERS", os.cpu_count() or 2))
    logger.info(f"🚀 Starting Task Service on port {SERVICE_PORT} with {workers} workers")

    # Heartbeat in main thread only (not in workers)
    t = threading.Thread(target=heartbeat_daemon, daemon=True)
    t.start()

    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=SERVICE_PORT,
        log_level="info",
        workers=workers,
    )


if __name__ == "__main__":
    main()
