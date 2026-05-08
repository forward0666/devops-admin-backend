from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routes import zones, dns, firewall, ssl, cache

app = FastAPI(title="Cloudflare Manager API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(zones.router, prefix="/zones", tags=["Zones"])
app.include_router(dns.router, prefix="/zones/{zone_id}/dns", tags=["DNS"])
app.include_router(firewall.router, prefix="/zones/{zone_id}/firewall", tags=["Firewall"])
app.include_router(ssl.router, prefix="/zones/{zone_id}/ssl", tags=["SSL"])
app.include_router(cache.router, prefix="/zones/{zone_id}/cache", tags=["Cache"])


@app.get("/health")
async def health():
    return {"status": "ok"}
