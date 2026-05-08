import os

NACOS_HOST = os.getenv("NACOS_HOST", "192.168.86.9")
NACOS_PORT = int(os.getenv("NACOS_PORT", "8848"))
NACOS_NAMESPACE = os.getenv("NACOS_NAMESPACE", "6c5b1db3-a808-4543-a87e-6642e372cb4f")
NACOS_USERNAME = os.getenv("NACOS_USERNAME", "nacos")
NACOS_PASSWORD = os.getenv("NACOS_PASSWORD", "nacos")

SERVICE_NAME = "cloudflare"
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8090"))
SERVICE_IP = os.getenv("SERVICE_IP", "127.0.0.1")

CF_BASE_URL = os.getenv("CF_BASE_URL", "https://api.cloudflare.com/client/v4")
#
# cd ~/Desktop/projects/devops-admin-backend/cloudflare
# python3.12 -m venv venv
# source venv/bin/activate
# pip3 install -r requirements.txt
#  pip install --upgrade pip
# python run.py
