package com.backend.bigdata.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import network.CloudflareLogUtils;
import network.ThreadPoolUtils;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

/**
 * Cloudflare 日志批量消费者（支持 traceId）
 * 中文注释：
 * - 使用与 Producer 统一的线程池
 * - 动态计算 batchSize
 * - 自动解析 JSON 并展平字段
 * - 支持高并发批量入库
 * - 全链路 traceId（可追踪每条日志）
 */
@Slf4j
public class CloudflareLogsBatchConsumer {

    private final ExecutorService executorService;
    private final Consumer<List<Map<String, Object>>> batchInsertFunction;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String topic;

    /**
     * 构造函数
     *
     * @param executorService       公共线程池
     * @param batchInsertFunction   批量写入数据库的回调函数
     * @param topic                 Kafka topic 或业务标识
     */
    public CloudflareLogsBatchConsumer(ExecutorService executorService,
                                       Consumer<List<Map<String, Object>>> batchInsertFunction,
                                       String topic) {
        this.executorService = executorService;
        this.batchInsertFunction = batchInsertFunction;
        this.topic = topic;
    }

    /**
     * 消费 Kafka 消息（带 traceId）
     *
     * @param messages 消息列表
     * @param traceId  全链路 traceId
     */

    public void consume(List<String> messages, String traceId) {
        if (messages == null || messages.isEmpty()) return;

        // ✅ 动态计算批次大小
        int batchSize = ThreadPoolUtils.calculateSmartBatchSize(executorService, messages.size(), 10, 200);
        log.info("🛠 [{}] traceId={} | Calculated dynamic batchSize={} (totalMessages={})",
                topic, traceId, batchSize, messages.size());

        // 拆分批次
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += batchSize) {
            batches.add(messages.subList(i, Math.min(i + batchSize, messages.size())));
        }

        // 并发提交任务
        for (List<String> batch : batches) {
            executorService.submit(() -> {
                MDC.put("traceId", traceId); // 设置线程 MDC
                log.info("🧵 [{}] Thread {} started processing {} messages | traceId={}",
                        topic, Thread.currentThread().getName(), batch.size(), traceId);
                try {
                    processBatch(batch, traceId);
                } finally {
                    log.info("🧵 [{}] Thread {} finished processing {} messages | traceId={}",
                            topic, Thread.currentThread().getName(), batch.size(), traceId);
                    MDC.remove("traceId"); // 清理 MDC
                }
            });
        }

        // 打印线程池状态
        if (executorService instanceof ThreadPoolExecutor tpe) {
            ThreadPoolUtils.logThreadPoolStatus(tpe, topic);
        }
    }

    /**
     * 批次处理逻辑：JSON解析 + 字段展平 + 批量写入
     */
    private void processBatch(List<String> messages, String traceId) {
        List<Map<String, Object>> batch = new ArrayList<>();
        for (String message : messages) {
            try {
                if (message != null && message.trim().startsWith("{") && message.trim().endsWith("}")) {
                    Map<String, Object> original = objectMapper.readValue(message, Map.class);
                    Map<String, Object> flattened = CloudflareLogUtils.flattenAndBuildParams(original);
                    batch.add(flattened);
                } else {
                    log.warn("⚠️ [{}] traceId={} | Skip malformed message: {}", topic, traceId, message);
                }
            } catch (Exception e) {
                log.warn("⚠️ [{}] traceId={} | Skip bad message: {}", topic, traceId, e.getMessage());
            }
        }

        if (!batch.isEmpty()) {
            try {
                batchInsertFunction.accept(batch);
                log.info("✅ [{}] traceId={} | Inserted {} records successfully", topic, traceId, batch.size());
            } catch (Exception e) {
                log.error("❌ [{}] traceId={} | Batch insert failed: {}", topic, traceId, e.getMessage(), e);
            }
        }
    }
}
