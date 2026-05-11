from fastapi import APIRouter, Header, HTTPException
from app.services import cf_client

router = APIRouter()


@router.get("/rules")
async def list_rules(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    try:
        return cf_client.list_firewall_rules(x_cf_token, zone_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/rules")
async def create_rule(zone_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    try:
        return cf_client.create_firewall_rule(x_cf_token, zone_id, body)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.put("/rules/{rule_id}")
async def update_rule(zone_id: str, rule_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token"), body: dict = None):
    try:
        return cf_client.update_firewall_rule(x_cf_token, zone_id, rule_id, body)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/rules/{rule_id}")
async def delete_rule(zone_id: str, rule_id: str, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    try:
        return cf_client.delete_firewall_rule(x_cf_token, zone_id, rule_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
