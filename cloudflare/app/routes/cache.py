from fastapi import APIRouter, Header, HTTPException
from app.services import cf_client

router = APIRouter()


@router.post("/purge")
async def purge_all(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    try:
        return cf_client.purge_all(x_cf_token, zone_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/purge/urls")
async def purge_urls(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    try:
        return cf_client.purge_by_urls(x_cf_token, zone_id, body.get("files", []))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/purge/tags")
async def purge_tags(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    try:
        return cf_client.purge_by_tags(x_cf_token, zone_id, body.get("tags", []))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/purge/hosts")
async def purge_hosts(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    try:
        return cf_client.purge_by_hosts(x_cf_token, zone_id, body.get("hosts", []))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
