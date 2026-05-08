from fastapi import APIRouter, Header, HTTPException
from app.services import cf_client

router = APIRouter()


@router.get("")
async def list_zones(x_cf_token: str = Header(..., alias="X-Cf-Token")):
    try:
        return cf_client.list_zones(x_cf_token)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{zone_id}")
async def get_zone(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    try:
        return cf_client.get_zone(x_cf_token, zone_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
