import uvicorn
from app.config import SERVICE_PORT

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=SERVICE_PORT, reload=True)
