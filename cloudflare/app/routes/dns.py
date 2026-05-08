from fastapi import APIRouter, Header, HTTPException
from app.services import cf_client

router = APIRouter()


@router.get("")
async def list_dns(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    try:
        return cf_client.list_dns(x_cf_token, zone_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("")
async def create_dns(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    try:
        return cf_client.create_dns(x_cf_token, zone_id, body)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.put("/{record_id}")
async def update_dns(zone_id: str, record_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    try:
        return cf_client.update_dns(x_cf_token, zone_id, record_id, body)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/{record_id}")
async def delete_dns(zone_id: str, record_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    try:
        return cf_client.delete_dns(x_cf_token, zone_id, record_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
