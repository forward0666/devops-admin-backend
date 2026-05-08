from fastapi import APIRouter, Header, HTTPException
from app.services import cf_client

router = APIRouter()


@router.get("")
async def get_ssl(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    try:
        return cf_client.get_ssl(x_cf_token, zone_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.patch("")
async def update_ssl(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    try:
        return cf_client.update_ssl(x_cf_token, zone_id, body.get("value", "full"))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
