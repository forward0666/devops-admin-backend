import httpx

BASE_URL = "https://api.cloudflare.com/client/v4"

async def cf_request(api_token: str, method: str, path: str, **kwargs):
    url = f"{BASE_URL}{path}"
    headers = {
        "Authorization": f"Bearer {api_token}",
        "Content-Type": "application/json",
    }
    async with httpx.AsyncClient(timeout=30.0) as client:
        resp = await client.request(method, url, headers=headers, **kwargs)
        return resp.json(), resp.status_code
