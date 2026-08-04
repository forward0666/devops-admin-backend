# Monitor Checker 优化踩坑记录

## 日期：2026-06-05

## 问题
1809 域名检测耗时 50+ 秒，100 域名只需 0.5 秒。

## 根因
`httpx.Limits(max_connections=2000)` 远大于实际并发数（50），导致：
1. httpx 在后台疯狂创建连接
2. TCP 端口耗尽，新连接排队
3. DNS 解析器被撑爆
4. per-request 时间从 150ms 涨到 900ms

## 修复
```python
# ❌ 错误：连接池远大于并发数
limits = httpx.Limits(max_connections=2000, max_keepalive_connections=500)

# ✅ 正确：连接池 = 并发数
limits = httpx.Limits(max_connections=CONCURRENCY, max_keepalive_connections=CONCURRENCY)
```

## 关键教训

### 1. 连接池必须匹配并发数
- `max_connections` 不是越大越好
- 过大的连接池导致端口耗尽、资源浪费
- 正确做法：`max_connections = 并发数`

### 2. DNS 并发也要限制
- 1809 个 DNS 查询同时打出去，DNS 服务器扛不住
- DNS 从 29ms 涨到 8305ms（286 倍）
- 需要单独的 DNS 并发限制

### 3. Semaphore 流式 > 严格分批
- 流式处理：慢域名不阻塞快域名
- 严格分批：每批等最慢的域名，总时间更长
- 结论：用 Semaphore，不用分批

### 4. HTTP/HTTPS 竞速要用 `as_completed`
- `asyncio.wait(FIRST_COMPLETED)` 返回第一个完成的（可能是失败的）
- `asyncio.as_completed()` 遍历所有完成的，找到第一个成功的
- 结论：用 `as_completed`，不用 `wait(FIRST_COMPLETED)`

### 5. aiodns 在 Alpine 上有问题
- aiodns 预解析 23 秒，缓存 0 条
- 系统 DNS（`asyncio.getaddrinfo`）反而更快更稳定
- 结论：用系统 DNS + dict 缓存

### 6. 单一 timeout 更简单有效
- `connect=1s, read=2s, write=1s, pool=3s` 太复杂
- 很多域名在 1s 内连不上，被误判为 down
- 结论：用单一 `timeout=5s`，简单可靠

### 7. 服务器网络有物理上限
- 100 域名：HTTP avg=150ms
- 1809 域名：HTTP avg=287ms（1.9 倍）
- 即使并发 50，per-request 也会涨，这是网络物理限制

## 最终配置
```python
CHECK_CONCURRENCY = 50   # HTTP 并发
DNS_CONCURRENCY = 50     # DNS 并发
TIMEOUT = 5.0            # 单一超时
max_connections = 50     # 连接池 = 并发数
```

## 结果
- 100 域名：0.54s
- 1809 域名：22.52s（从 50s 优化到 22s）
