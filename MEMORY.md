# MEMORY.md — 项目记忆

> 长期记忆，每次继续任务前先读此文件。

## 项目结构

```
/root/workspace/devops-admin-backend/
├── docs/                         # 项目文档
│   ├── ARCHITECTURE.md           # 架构说明
│   ├── INFRA.md                  # 基础设施清单（已配端口/密码）
│   └── WORKFLOW.md               # 构建/部署流程
├── scripts/                      # 运维脚本
│   ├── build/
│   │   └── build-all.sh          # 一键构建
│   ├── harbor/
│   │   ├── gen-harbor-certs.sh
│   │   └── patch-harbor-tls.py
│   └── k8s/
│       ├── configure-containerd-harbor.sh
│       ├── set-nodeports.py
│       └── write-nacos-config.py
└── TASKS.md                      # 任务进度
```

## 集群架构

| 项目 | 值 |
|------|-----|
| 集群 | K3s v1.31.14 三节点 |
| Master | 192.168.86.12 (k8s-master) |
| Worker | 192.168.86.14 (k8s-worker) |
| 存储 | 192.168.86.13 (longhorn) |
| StorageClass | longhorn (default) |
| Runtime | containerd v1.7.29 |

## 关键地址速查

| 服务 | 内部地址 | NodePort |
|------|----------|----------|
| Harbor | harbor.harbor:80 | 32477 |
| Nacos | nacos.middleware:8848 | 30103 |
| Traefik | - | 30165 |
| ArgoCD | argocd-server:443 | 33101 |

## Nacos 配置

所有模块配置通过 Nacos 管理，dataId = `<模块>.properties`，group = DEFAULT_GROUP。
配置已写入 MySQL（nacos.config_info 表）。

## 构建流程

```bash
# 1. 本地改代码 → push
git push https://forward0666:${GITHUB_TOKEN}@github.com/forward0666/devops-admin-backend.git fix_bug

# 2. 登录 master 拉取 + 一键构建
ssh ubuntu@192.168.86.12
cd /tmp/devops-admin-backend && git pull https://forward0666:${GITHUB_TOKEN}@github.com/forward0666/devops-admin-backend.git fix_bug
bash /root/workspace/devops-admin-backend/scripts/build/build-all.sh
```

## 已知问题

1. Jackson 版本冲突 — 已加 jackson-bom 2.17.3 修复
2. Harbor TLS — 证书已生成，nginx 配置待完善
3. containerd certs.d — master/worker 已配

## 凭据

所有资产信息（节点、端口、密码、Token）统一在 /root/.hermes/INVENTORY.md
项目文档在 /root/workspace/devops-admin-backend/docs/
构建流程在 WORKFLOW.md