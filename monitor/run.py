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


def get_cpu_count() -> int:
    try:
        with open("/sys/fs/cgroup/cpu.max") as f:
            parts = f.read().strip().split()
            if parts[0] != "max":
                return int(int(parts[0]) / int(parts[1]))
    except Exception:
        pass
    try:
        with open("/sys/fs/cgroup/cpu/cpu.cfs_quota_us") as f:
            quota = int(f.read().strip())
            if quota > 0:
                with open("/sys/fs/cgroup/cpu/cpu.cfs_period_us") as f:
                    period = int(f.read().strip())
                return int(quota / period)
    except Exception:
        pass
    return os.cpu_count() or 2


def heartbeat_daemon():
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
    workers = int(os.getenv("UVICORN_WORKERS", get_cpu_count() * 2))
    logger.info(f"🚀 Starting Monitor Service on port {SERVICE_PORT} with {workers} workers")

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
