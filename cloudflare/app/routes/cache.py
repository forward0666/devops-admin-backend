from fastapi import APIRouter, Header, HTTPException
from app.services import cf_client

router = APIRouter()


@router.post("/purge")
async def purge_all(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    body = {"purge_everything": True}
    data, status = await cf_client.cf_request(x_cf_token, "POST", f"/zones/{zone_id}/purge_cache", json=body)
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data


@router.post("/purge/urls")
async def purge_urls(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    data, status = await cf_client.cf_request(x_cf_token, "POST", f"/zones/{zone_id}/purge_cache", json=body)
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data


@router.post("/purge/tags")
async def purge_tags(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    data, status = await cf_client.cf_request(x_cf_token, "POST", f"/zones/{zone_id}/purge_cache", json=body)
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data


@router.post("/purge/hosts")
async def purge_hosts(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    data, status = await cf_client.cf_request(x_cf_token, "POST", f"/zones/{zone_id}/purge_cache", json=body)
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data
