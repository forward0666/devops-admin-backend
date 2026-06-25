import logging
from fastapi import APIRouter, HTTPException, Query
from app.services import cf_client
from app.services.db import query_one, query_all as query_all_sql
from app.services.mongodb import get_db

logger = logging.getLogger(__name__)

router = APIRouter()


async def _get_cf(account_id: int):
    """Get CF client for account. Returns (cf_client, cf_account_id)."""
    row = await query_one("SELECT api_key, cf_account_id FROM account WHERE id = %s", (account_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Account not found")
    cf = await cf_client.async_get_client(row["api_key"])
    return cf, row["cf_account_id"] or str(account_id)


@router.get("")
async def list_lists(account_id: int = Query(None)):
    """GET /configurations/lists - List all lists for an account (or all accounts)."""
    from app.services.db import query_all as query_all_sql
    if account_id:
        accounts = [{"id": account_id}]
    else:
        accounts = await query_all_sql("SELECT id FROM account")
    logger.info(f"[Sync] Start lists: account_id={account_id or 'all'}, accounts={[a['id'] for a in accounts]}")
    all_lists = []
    for acc in accounts:
        try:
            cf, cf_account_id = await _get_cf(acc["id"])
            resp = cf.rules.lists.list(account_id=cf_account_id)
            count = 0
            for item in resp:
                d = item.model_dump(mode="json") if hasattr(item, 'model_dump') else item.__dict__
                d["account_id"] = acc["id"]
                all_lists.append(d)
                count += 1
            logger.info(f"[Sync] Account {acc['id']}: {count} IP lists")
        except Exception as e:
            logger.warning(f"list_lists for account {acc['id']} error: {e}")
    return {"code": 200, "data": all_lists}


@router.get("/all")
async def get_all_items():
    """GET /configurations/lists/all - Read cached items from MongoDB."""
    try:
        db = await get_db()
        cursor = db.cf_list_items.find({}, {"_id": 0})
        items = await cursor.to_list(length=None)
        # Ensure all items are JSON-serializable (no ObjectId)
        clean_items = [{k: str(v) if not isinstance(v, (str, int, float, bool, list, dict, type(None))) else v for k, v in item.items()} for item in items]
        return {"code": 200, "data": clean_items}
    except Exception as e:
        logger.error(f"get_all_items error: {e}")
        return {"code": 200, "data": []}


@router.post("/all/store")
async def store_list_items(body: dict):
    """POST /configurations/lists/all/store - Store items for a specific list in MongoDB."""
    list_id = body.get("list_id")
    list_name = body.get("list_name", "")
    items = body.get("items", [])
    if not list_id:
        raise HTTPException(status_code=400, detail="list_id required")
    try:
        db = await get_db()
        # Remove old items for this list
        db.cf_list_items.delete_many({"list_id": list_id})
        # Add list metadata to each item
        for item in items:
            item["list_id"] = list_id
            item["list_name"] = list_name
            item["list_kind"] = body.get("list_kind", "ip")
        if items:
            db.cf_list_items.insert_many(items)
        logger.info(f"[Store] list_id={list_id} ({list_name}): {len(items)} items")
        return {"code": 200, "stored": len(items)}
    except Exception as e:
        logger.error(f"store_list_items error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/sync")
async def sync_all_items(account_id: int = Query(None)):
    """GET /configurations/lists/sync - Fetch all lists + items from all accounts."""
    from app.services.db import query_all as query_all_sql
    if account_id:
        accounts = [{"id": account_id}]
    else:
        accounts = await query_all_sql("SELECT id FROM account")
    logger.info(f"[Sync All] Start: accounts={[a['id'] for a in accounts]}")

    # 1. Fetch all lists
    all_lists = []
    for acc in accounts:
        try:
            cf, cf_account_id = await _get_cf(acc["id"])
            resp = cf.rules.lists.list(account_id=cf_account_id)
            for item in resp:
                d = item.model_dump(mode="json") if hasattr(item, 'model_dump') else item.__dict__
                d["account_id"] = acc["id"]
                all_lists.append(d)
        except Exception as e:
            logger.warning(f"[Sync All] list_lists account {acc['id']} error: {e}")
    logger.info(f"[Sync All] Found {len(all_lists)} IP lists")

    # Build CF clients per account
    account_clients = {}
    for acc in accounts:
        try:
            cf, cf_account_id = await _get_cf(acc["id"])
            account_clients[acc["id"]] = (cf, cf_account_id)
        except Exception as e:
            logger.warning(f"[Sync All] client account {acc['id']} error: {e}")

    # 2. Fetch items per account (parallel across accounts) and store to MongoDB
    import asyncio

    db = await get_db()

    total_items = 0

    async def _fetch_account_items(acc_id, lists):
        nonlocal total_items
        if acc_id not in account_clients:
            return
        cf, cf_account_id = account_clients[acc_id]
        for lst in lists:
            try:
                resp = cf.rules.lists.items.list(list_id=lst["id"], account_id=cf_account_id)
                batch = []
                for item in resp:
                    d = item.model_dump(mode="json") if hasattr(item, 'model_dump') else item.__dict__
                    d["list_name"] = lst.get("name", "")
                    d["list_id"] = lst["id"]
                    d["list_kind"] = lst.get("kind", "ip")
                    batch.append(d)
                # Replace items for this list: delete old, insert new
                if batch:
                    try:
                        db = await get_db()
                        await db.cf_list_items.delete_many({"list_id": lst["id"]})
                        await db.cf_list_items.insert_many(batch)
                    except Exception as e:
                        logger.error(f"[Sync All] MongoDB insert list_id={lst['id']} error: {e}")
                total_items += len(batch)
                logger.info(f"[Sync All] {lst.get('name')}: {len(batch)} items (total: {total_items})")
            except Exception as e:
                logger.warning(f"[Sync All] items list_id={lst['id']} error: {e}")

    lists_by_account: dict[int, list] = {}
    for lst in all_lists:
        lists_by_account.setdefault(lst["account_id"], []).append(lst)

    await asyncio.gather(*[_fetch_account_items(aid, lts) for aid, lts in lists_by_account.items()])
    logger.info(f"[Sync All] Done: {total_items} items from {len(all_lists)} lists")

    return {"code": 200, "data": [], "synced": total_items}


@router.post("")
async def create_list(body: dict, account_id: int = Query(...)):
    """POST /configurations/lists - Create a new list."""
    name = (body.get("name") or "").strip()
    if not name:
        raise HTTPException(status_code=400, detail="name is required")

    kind = body.get("kind") or "ip"
    description = (body.get("description") or "").strip()

    cf, cf_account_id = await _get_cf(account_id)
    try:
        kwargs = {
            "name": name,
            "kind": kind,
            "account_id": cf_account_id,
        }
        if description:
            kwargs["description"] = description
        resp = cf.rules.lists.create(**kwargs)
        data = resp.model_dump(mode="json") if hasattr(resp, 'model_dump') else resp.__dict__
        return {"code": 200, "data": data, "message": "List created"}
    except Exception as e:
        logger.error(f"create_list error: {e}")
        raise HTTPException(status_code=400, detail=str(e))


@router.put("/{list_id}")
async def update_list(list_id: str, body: dict, account_id: int = Query(...)):
    """PUT /configurations/lists/{list_id} - Update a list."""
    cf, cf_account_id = await _get_cf(account_id)
    try:
        kwargs = {
            "list_id": list_id,
            "account_id": cf_account_id,
        }
        if "name" in body:
            kwargs["name"] = body["name"]
        if "description" in body:
            kwargs["description"] = body["description"]
        resp = cf.rules.lists.update(**kwargs)
        data = resp.model_dump(mode="json") if hasattr(resp, 'model_dump') else resp.__dict__
        return {"code": 200, "data": data, "message": "List updated"}
    except Exception as e:
        logger.error(f"update_list error: {e}")
        raise HTTPException(status_code=400, detail=str(e))


@router.delete("/{list_id}")
async def delete_list(list_id: str, account_id: int = Query(...)):
    """DELETE /configurations/lists/{list_id} - Delete a list."""
    cf, cf_account_id = await _get_cf(account_id)
    try:
        cf.rules.lists.delete(list_id=list_id, account_id=cf_account_id)
        return {"code": 200, "message": "List deleted"}
    except Exception as e:
        logger.error(f"delete_list error: {e}")
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/{list_id}/items")
async def list_items(list_id: str, account_id: int = Query(...)):
    """GET /configurations/lists/{list_id}/items - List items in a list."""
    logger.info(f"[Sync] Start list items: list_id={list_id}, account_id={account_id}")
    cf, cf_account_id = await _get_cf(account_id)
    try:
        resp = cf.rules.lists.items.list(list_id=list_id, account_id=cf_account_id)
        items = []
        for item in resp:
            d = item.model_dump(mode="json") if hasattr(item, 'model_dump') else item.__dict__
            logger.info(f"[List Item] {d}")
            items.append(d)
        return {"code": 200, "data": items}
    except Exception as e:
        logger.error(f"list_items error: {e}")
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/{list_id}/items")
async def add_items(list_id: str, body: dict, account_id: int = Query(...)):
    """POST /configurations/lists/{list_id}/items - Add items to a list."""
    items = body.get("items") or []
    if not items:
        raise HTTPException(status_code=400, detail="items are required")

    logger.info(f"[Add Items] Step 1 - list_id={list_id}, account_id={account_id}, items={items}")
    cf, cf_account_id = await _get_cf(account_id)
    logger.info(f"[Add Items] Step 2 - cf_account_id={cf_account_id}, calling CF API...")
    try:
        resp = cf.rules.lists.items.create(
            list_id=list_id,
            account_id=cf_account_id,
            body=items,
        )
        data = resp.model_dump(mode="json") if hasattr(resp, 'model_dump') else resp.__dict__
        logger.info(f"[Add Items] Step 3 - Success: {data}")
        return {"code": 200, "data": data, "message": "Items added"}
    except Exception as e:
        logger.error(f"[Add Items] Step 3 - Error: {e}")
        raise HTTPException(status_code=400, detail=str(e))


@router.put("/{list_id}/items")
async def update_items(list_id: str, body: dict, account_id: int = Query(...)):
    """PUT /configurations/lists/{list_id}/items - Update a single item (fetch all, replace)."""
    items = body.get("items") or []
    if not items:
        raise HTTPException(status_code=400, detail="items are required")

    update_item = items[0]  # The item to update (must have 'id')
    item_id = update_item.get("id")
    if not item_id:
        raise HTTPException(status_code=400, detail="item id is required")

    # Determine list kind from update item or fetch it
    list_kind = body.get("list_kind", "ip")

    cf, cf_account_id = await _get_cf(account_id)
    # Get raw api_key for direct HTTP call
    acc_row = await query_one("SELECT api_key FROM account WHERE id = %s", (account_id,))
    api_key = acc_row["api_key"] if acc_row else ""
    try:
        # 1. Fetch all current items
        current = cf.rules.lists.items.list(list_id=list_id, account_id=cf_account_id)
        all_items = []
        sample_logged = False
        for item in current:
            d = item.model_dump(mode="json") if hasattr(item, 'model_dump') else item.__dict__
            if not sample_logged:
                logger.info(f"[update_items] sample item raw: {d}")
                sample_logged = True
            if d.get("id") == item_id:
                # Replace with updated data
                entry = {"id": item_id}
                if list_kind == "asn":
                    entry["asn"] = update_item.get("asn", d.get("asn", 0))
                elif list_kind == "hostname":
                    entry["hostname"] = update_item.get("hostname", d.get("hostname", {}))
                elif list_kind == "redirect":
                    entry["redirect"] = update_item.get("redirect", d.get("redirect", {}))
                else:  # ip
                    new_ip = update_item.get("ip", {})
                    if isinstance(new_ip, dict):
                        entry["ip"] = new_ip.get("ip", d.get("ip", ""))
                        if new_ip.get("comment") is not None:
                            entry["comment"] = new_ip["comment"]
                        elif d.get("comment"):
                            entry["comment"] = d["comment"]
                    else:
                        entry["ip"] = new_ip or d.get("ip", "")
                        if d.get("comment"):
                            entry["comment"] = d["comment"]
                if "comment" not in entry and d.get("comment"):
                    entry["comment"] = d["comment"]
                all_items.append(entry)
            else:
                # Keep existing item - only include id + type-specific field + comment
                entry = {"id": d["id"]}
                if list_kind == "asn":
                    entry["asn"] = d.get("asn", 0)
                elif list_kind == "hostname":
                    entry["hostname"] = d.get("hostname", {})
                elif list_kind == "redirect":
                    entry["redirect"] = d.get("redirect", {})
                else:
                    entry["ip"] = d.get("ip", "")
                if d.get("comment"):
                    entry["comment"] = d["comment"]
                all_items.append(entry)

        # 2. PUT all items back - use raw httpx to avoid SDK stripping 'id'
        logger.info(f"[update_items] list_id={list_id}, list_kind={list_kind}, items_count={len(all_items)}")
        logger.info(f"[update_items] body: {all_items}")
        import httpx
        url = f"https://api.cloudflare.com/client/v4/accounts/{cf_account_id}/rules/lists/{list_id}/items"
        headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
        # Try without 'id' - CF API may not accept id in body
        items_no_id = [{k: v for k, v in item.items() if k != "id"} for item in all_items]
        logger.info(f"[update_items] body (no id): {items_no_id}")
        async with httpx.AsyncClient(timeout=30) as http:
            r = await http.put(url, headers=headers, json={"items": items_no_id})
        logger.info(f"[update_items] CF response (no id): {r.status_code} {r.text[:500]}")
        if r.status_code == 200:
            return {"code": 200, "data": r.json(), "message": "Item updated"}
        else:
            raise HTTPException(status_code=r.status_code, detail=r.text)
    except Exception as e:
        logger.error(f"update_items error: {e}")
        raise HTTPException(status_code=400, detail=str(e))


@router.delete("/{list_id}/items")
async def remove_items(list_id: str, body: dict, account_id: int = Query(...)):
    """DELETE /configurations/lists/{list_id}/items - Remove items from a list."""
    items = body.get("items") or []
    if not items:
        raise HTTPException(status_code=400, detail="items are required")

    cf, cf_account_id = await _get_cf(account_id)
    try:
        cf.rules.lists.items.delete(
            list_id=list_id,
            account_id=cf_account_id,
            items=items,
        )
        return {"code": 200, "message": "Items removed"}
    except Exception as e:
        logger.error(f"remove_items error: {e}")
        raise HTTPException(status_code=400, detail=str(e))
