from fastapi import APIRouter, Header, HTTPException
from app.services import cf_client

router = APIRouter()


@router.get("")
async def list_zones(x_cf_token: str = Header(..., alias="X-Cf-Token")):
    data, status = await cf_client.cf_request(x_cf_token, "GET", "/zones?per_page=50")
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data


@router.get("/{zone_id}")
async def get_zone(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    data, status = await cf_client.cf_request(x_cf_token, "GET", f"/zones/{zone_id}")
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data


@router.get("/{zone_id}/settings")
async def get_zone_settings(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    data, status = await cf_client.cf_request(x_cf_token, "GET", f"/zones/{zone_id}/settings")
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data
