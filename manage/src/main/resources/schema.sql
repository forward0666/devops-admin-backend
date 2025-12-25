-- ========================================
-- ClickHouse数据库维护脚本
-- 用于清理Cloudflare日志数据，删除指定时间范围外的记录
-- ========================================

-- 删除http_u8_app表中2025年8月1日之前和2025年10月1日之后的记录
-- 清理过期的Cloudflare访问日志数据
ALTER TABLE cloudflare_logs.http_u8_app
DELETE WHERE EdgeStartTimestamp < toDate('2025-08-01')
          OR EdgeStartTimestamp >= toDate('2025-10-01');

-- 显示http_u8_app表的创建语句
-- 用于查看表结构和索引信息
SHOW CREATE TABLE cloudflare_logs.http_u8_app;

-- 删除http_gogame_app_local表中2025年8月1日之前和2025年10月1日之后的记录
-- 清理本地表的过期Cloudflare游戏应用日志数据
ALTER TABLE cloudflare_logs.http_gogame_app_local
DELETE WHERE EdgeStartTimestamp < toDate('2025-08-01')
          OR EdgeStartTimestamp >= toDate('2025-10-01');

-- 统计http_u8_app表中的记录数量
-- 用于验证删除操作后的数据量
SELECT COUNT(*) FROM cloudflare_logs.http_u8_app;

-- 统计http_u8_app_local表中的记录数量
-- 用于验证本地表的记录数量
SELECT COUNT(*) FROM cloudflare_logs.http_u8_app_local;

-- 优化http_u8_app_local表
-- FINAL选项强制立即执行优化，合并数据部分并释放磁盘空间
OPTIMIZE TABLE cloudflare_logs.http_u8_app_local FINAL;

-- 在集群所有节点上删除http_u8_app_local表中的过期记录
-- ON CLUSTER default表示在整个默认集群上执行删除操作
ALTER TABLE cloudflare_logs.http_u8_app_local
ON CLUSTER default
DELETE WHERE EdgeStartTimestamp < toDate('2025-08-01')
          OR EdgeStartTimestamp >= toDate('2025-10-01');
-- 再次统计两个表的记录数量，验证集群范围的删除操作
SELECT COUNT(*) FROM cloudflare_logs.http_u8_app;
SELECT COUNT(*) FROM cloudflare_logs.http_u8_app_local;


-- 查询默认集群的分片和副本信息
-- 显示集群的拓扑结构，包括分片号、副本号和主机名
SELECT shard_num, replica_num, host_name
FROM system.clusters
WHERE cluster = 'default';


ALTER TABLE cloudflare_logs.http_u8_app_local
ON CLUSTER default
DELETE WHERE EdgeStartTimestamp < toDate('2025-08-01')
          OR EdgeStartTimestamp >= toDate('2025-10-01');
OPTIMIZE TABLE cloudflare_logs.http_u8_app_local ON CLUSTER default FINAL;
-- 查询时间范围统计：记录数量、最小时间戳、最大时间戳
-- 用于验证数据的时间范围是否符合预期
SELECT count(), min(EdgeStartTimestamp), max(EdgeStartTimestamp)
FROM cloudflare_logs.http_u8_app_local;
SELECT count(), min(EdgeStartTimestamp), max(EdgeStartTimestamp)
FROM cloudflare_logs.http_u8_app;

SELECT
    database,
    formatReadableSize(sum(bytes_on_disk)) AS disk_size,
    sum(rows) AS total_rows
FROM system.parts
WHERE active
  AND database = 'cloudflare_logs'
GROUP BY database;


SELECT count(), min(EdgeStartTimestamp), max(EdgeStartTimestamp)
FROM cloudflare_logs.http_u8_app_local;
SELECT count(), min(EdgeStartTimestamp), max(EdgeStartTimestamp)
FROM cloudflare_logs.http_u8_app;

-- 查看活跃数据部分的大小（删除操作前后对比）
-- 监控磁盘空间使用情况和数据行数统计
SELECT
    table,
    formatReadableSize(sum(bytes_on_disk)) AS disk_size,
    sum(rows) AS total_rows
FROM system.parts
WHERE active
  AND table = 'http_u8_app_local'
GROUP BY table;

SELECT * FROM system.merges;

