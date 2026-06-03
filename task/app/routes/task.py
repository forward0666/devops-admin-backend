import logging
from datetime import datetime
from bson import ObjectId

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional

from app.services.mongodb import get_db

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
    db = await get_db()
    tasks = []
    async for doc in db.tasks.find().sort("created_at", -1):
        doc["id"] = str(doc["_id"])
        del doc["_id"]
        tasks.append(doc)
    return {"data": tasks, "total": len(tasks)}


@router.post("/tasks")
async def create_task(body: TaskCreate):
    """Create a new task"""
    db = await get_db()
    doc = {
        "name": body.name,
        "type": body.type,
        "cron": body.cron,
        "enabled": body.enabled,
        "description": body.description,
        "config": body.config,
        "created_at": datetime.utcnow(),
        "updated_at": datetime.utcnow(),
        "last_run_at": None,
        "last_status": None,
    }
    result = await db.tasks.insert_one(doc)
    return {"id": str(result.inserted_id), "message": "Task created"}


@router.get("/tasks/{task_id}")
async def get_task(task_id: str):
    """Get task by ID"""
    db = await get_db()
    doc = await db.tasks.find_one({"_id": ObjectId(task_id)})
    if not doc:
        raise HTTPException(status_code=404, detail="Task not found")
    doc["id"] = str(doc["_id"])
    del doc["_id"]
    return doc


@router.put("/tasks/{task_id}")
async def update_task(task_id: str, body: TaskUpdate):
    """Update a task"""
    db = await get_db()
    update = {k: v for k, v in body.model_dump().items() if v is not None}
    if not update:
        return {"message": "Nothing to update"}
    update["updated_at"] = datetime.utcnow()
    result = await db.tasks.update_one({"_id": ObjectId(task_id)}, {"$set": update})
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="Task not found")
    return {"message": "Task updated"}


@router.patch("/tasks/{task_id}")
async def patch_task(task_id: str, body: TaskUpdate):
    """Partial update a task"""
    return await update_task(task_id, body)


@router.delete("/tasks/{task_id}")
async def delete_task(task_id: str):
    """Delete a task"""
    db = await get_db()
    result = await db.tasks.delete_one({"_id": ObjectId(task_id)})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Task not found")
    return {"message": "Task deleted"}


@router.post("/tasks/{task_id}/run")
async def run_task(task_id: str):
    """Manually trigger a task"""
    db = await get_db()
    doc = await db.tasks.find_one({"_id": ObjectId(task_id)})
    if not doc:
        raise HTTPException(status_code=404, detail="Task not found")
    # TODO: Implement task execution logic per type
    logger.info(f"▶️ Task triggered manually: {doc['name']} (type={doc['type']})")
    return {"message": f"Task '{doc['name']}' triggered", "task_id": task_id}
