from fastapi import APIRouter, Header, HTTPException
from app.services import cf_client

router = APIRouter()


@router.get("/rules")
async def list_rules(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    data, status = await cf_client.cf_request(x_cf_token, "GET", f"/zones/{zone_id}/firewall/rules")
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data


@router.post("/rules")
async def create_rule(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    data, status = await cf_client.cf_request(x_cf_token, "POST", f"/zones/{zone_id}/firewall/rules", json=body)
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data, 201


@router.put("/rules/{rule_id}")
async def update_rule(zone_id: str, rule_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    data, status = await cf_client.cf_request(x_cf_token, "PUT", f"/zones/{zone_id}/firewall/rules/{rule_id}", json=body)
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data


@router.delete("/rules/{rule_id}")
async def delete_rule(zone_id: str, rule_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    data, status = await cf_client.cf_request(x_cf_token, "DELETE", f"/zones/{zone_id}/firewall/rules/{rule_id}")
    if not data.get("success"):
        raise HTTPException(status_code=status, detail=data.get("errors", []))
    return data
