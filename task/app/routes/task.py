import logging
from datetime import datetime

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional

from app.services.db import query_all, query_one, execute
from app.services.scheduler import reload_scheduler

logger = logging.getLogger(__name__)
router = APIRouter()


# ─── Schemas ─────────────────────────────────────────────
class TaskCreate(BaseModel):
    name: str
    type: str
    cron: str
    enabled: bool = True
    description: str = ""
    config: dict = {}


class TaskUpdate(BaseModel):
    name: Optional[str] = None
    type: Optional[str] = None
    cron: Optional[str] = None
    enabled: Optional[bool] = None
    description: Optional[str] = None
    config: Optional[dict] = None


# ─── CRUD ─────────────────────────────────────────────────
@router.get("/tasks")
async def list_tasks():
    """List all tasks"""
    rows = await query_all("SELECT * FROM task ORDER BY created_at DESC")
    for row in rows:
        if isinstance(row.get("config"), str):
            import json
            try:
                row["config"] = json.loads(row["config"])
            except Exception:
                row["config"] = {}
    return {"data": rows, "total": len(rows)}


@router.post("/tasks")
async def create_task(body: TaskCreate):
    """Create a new task"""
    import json
    await execute(
        "INSERT INTO task (name, type, cron, enabled, description, config) VALUES (%s, %s, %s, %s, %s, %s)",
        (body.name, body.type, body.cron, int(body.enabled), body.description, json.dumps(body.config))
    )
    row = await query_one("SELECT * FROM task ORDER BY id DESC LIMIT 1")
    await reload_scheduler()
    return {"id": row["id"], "message": "Task created"}


@router.get("/tasks/{task_id}")
async def get_task(task_id: int):
    """Get task by ID"""
    row = await query_one("SELECT * FROM task WHERE id=%s", (task_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Task not found")
    if isinstance(row.get("config"), str):
        import json
        try:
            row["config"] = json.loads(row["config"])
        except Exception:
            row["config"] = {}
    return row


@router.put("/tasks/{task_id}")
async def update_task(task_id: int, body: TaskUpdate):
    """Update a task"""
    update = {k: v for k, v in body.model_dump().items() if v is not None}
    if not update:
        return {"message": "Nothing to update"}
    sets = []
    args = []
    for k, v in update.items():
        if k == "enabled":
            sets.append(f"{k}=%s")
            args.append(int(v))
        elif k == "config":
            sets.append(f"{k}=%s")
            import json
            args.append(json.dumps(v))
        else:
            sets.append(f"{k}=%s")
            args.append(v)
    args.append(task_id)
    await execute(f"UPDATE task SET {', '.join(sets)} WHERE id=%s", tuple(args))
    await reload_scheduler()
    return {"message": "Task updated"}


@router.patch("/tasks/{task_id}")
async def patch_task(task_id: int, body: TaskUpdate):
    """Partial update a task"""
    return await update_task(task_id, body)


@router.delete("/tasks/{task_id}")
async def delete_task(task_id: int):
    """Delete a task"""
    count = await execute("DELETE FROM task WHERE id=%s", (task_id,))
    if count == 0:
        raise HTTPException(status_code=404, detail="Task not found")
    await reload_scheduler()
    return {"message": "Task deleted"}


@router.post("/tasks/{task_id}/run")
async def run_task(task_id: int):
    """Manually trigger a task"""
    row = await query_one("SELECT * FROM task WHERE id=%s", (task_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Task not found")
    import json, asyncio
    if isinstance(row.get("config"), str):
        try:
            row["config"] = json.loads(row["config"])
        except Exception:
            row["config"] = {}
    from app.services.executor import execute_task
    asyncio.create_task(execute_task(row))
    return {"message": f"Task '{row['name']}' triggered", "task_id": task_id}
