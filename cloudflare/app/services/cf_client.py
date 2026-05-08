import httpx
import logging

logger = logging.getLogger(__name__)


async def cf_request(api_token: str, method: str, path: str, **kwargs):
    from app.config import CF_BASE_URL
    url = f"{CF_BASE_URL}{path}"
    headers = {
        "Authorization": f"Bearer {api_token}",
        "Content-Type": "application/json",
    }
    async with httpx.AsyncClient(timeout=30.0) as client:
        resp = await client.request(method, url, headers=headers, **kwargs)
        return resp.json(), resp.status_code
