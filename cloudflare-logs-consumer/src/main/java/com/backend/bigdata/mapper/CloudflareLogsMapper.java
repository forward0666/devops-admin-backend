package com.backend.bigdata.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

/**
 * Cloudflare日志数据访问接口
 * 中文注释：提供批量插入Cloudflare HTTP日志到不同应用数据库的方法
 * 使用MyBatis注解方式定义数据库操作
 */
@Mapper
public interface CloudflareLogsMapper {
    /**
     * 批量插入Cloudflare HTTP日志到U8应用数据库
     * @param batch 批量日志数据列表，每个元素为字段名到值的映射
     */
    void batchInsertCloudflareHttpLogsForU8App(List<Map<String, Object>> batch);

    /**
     * 批量插入Cloudflare HTTP日志到UU8应用数据库
     * @param batch 批量日志数据列表，每个元素为字段名到值的映射
     */
    void batchInsertCloudflareHttpLogsForUU8App(List<Map<String, Object>> batch);

    /**
     * 批量插入Cloudflare HTTP日志到Credit应用数据库
     * @param batch 批量日志数据列表，每个元素为字段名到值的映射
     */
    void batchInsertCloudflareHttpLogsForCreditApp(List<Map<String, Object>> batch);

    /**
     * 批量插入Cloudflare HTTP日志到GoGame应用数据库
     * @param batch 批量日志数据列表，每个元素为字段名到值的映射
     */
    void batchInsertCloudflareHttpLogsForGoGameApp(List<Map<String, Object>> batch);

    /**
     * 批量插入Cloudflare HTTP日志到KK应用数据库
     * @param batch 批量日志数据列表，每个元素为字段名到值的映射
     */
    void batchInsertCloudflareHttpLogsForKKApp(List<Map<String, Object>> batch);

    /**
     * 批量插入Cloudflare HTTP日志到Devop应用数据库
     * @param batch 批量日志数据列表，每个元素为字段名到值的映射
     */
    void batchInsertCloudflareHttpLogsForDevopApp(List<Map<String, Object>> batch);
}