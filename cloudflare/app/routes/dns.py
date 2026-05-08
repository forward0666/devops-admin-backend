from fastapi import APIRouter, Header, HTTPException, Request
from app.services import cf_client

router = APIRouter()


@router.get("")
async def list_dns(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    data, status = await cf_client.cf_request(x_cf_token, "GET", f"/zones/{zone_id}/dns_records?per_page=100")
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data


@router.post("")
async def create_dns(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    data, status = await cf_client.cf_request(x_cf_token, "POST", f"/zones/{zone_id}/dns_records", json=body)
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data, 201


@router.put("/{record_id}")
async def update_dns(zone_id: str, record_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    data, status = await cf_client.cf_request(x_cf_token, "PUT", f"/zones/{zone_id}/dns_records/{record_id}", json=body)
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data


@router.delete("/{record_id}")
async def delete_dns(zone_id: str, record_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    data, status = await cf_client.cf_request(x_cf_token, "DELETE", f"/zones/{zone_id}/dns_records/{record_id}")
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data
