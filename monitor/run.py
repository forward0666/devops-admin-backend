import asyncio
import logging
import os
import uvicorn

from app.config import SERVICE_PORT
from app.services.nacos_client import fetch_config, register_service

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)


def get_cpu_count() -> int:
    """Get pod CPU limit from cgroup, fallback to os.cpu_count()"""
    try:
        # cgroups v2
        with open("/sys/fs/cgroup/cpu.max") as f:
            parts = f.read().strip().split()
            if parts[0] != "max":
                return int(int(parts[0]) / int(parts[1]))
    except Exception:
        pass
    try:
        # cgroups v1
        with open("/sys/fs/cgroup/cpu/cpu.cfs_quota_us") as f:
            quota = int(f.read().strip())
        if quota > 0:
            with open("/sys/fs/cgroup/cpu/cpu.cfs_period_us") as f:
                period = int(f.read().strip())
            return int(quota / period)
    except Exception:
        pass
    return os.cpu_count() or 2


def main():
    # 1. Pull config from Nacos (overrides defaults)
    asyncio.run(fetch_config())

    # 2. Register service to Nacos (uses possibly overridden values)
    asyncio.run(register_service())

    # 3. Start FastAPI server with dynamic workers based on CPU
    from app.config import SERVICE_PORT  # re-read after config override
    workers = int(os.getenv("UVICORN_WORKERS", get_cpu_count() * 2))
    logger.info(f"🚀 Starting Monitor Service on port {SERVICE_PORT} with {workers} workers")
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=SERVICE_PORT,
        log_level="info",
        workers=workers,
    )


if __name__ == "__main__":
    main()
