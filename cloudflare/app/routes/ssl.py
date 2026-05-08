from fastapi import APIRouter, Header, HTTPException
from app.services import cf_client

router = APIRouter()


@router.get("")
async def get_ssl(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    data, status = await cf_client.cf_request(x_cf_token, "GET", f"/zones/{zone_id}/settings/ssl")
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data


@router.patch("")
async def update_ssl(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    data, status = await cf_client.cf_request(x_cf_token, "PATCH", f"/zones/{zone_id}/settings/ssl", json=body)
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data
