package com.backend.bigdata.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import exception.ExceptionUtils;
import lombok.extern.slf4j.Slf4j;
import network.CloudflareLogUtils;
import network.ThreadPoolUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

/**
 * Cloudflare 日志批量消费者（只打印业务日志）
 */
@Slf4j
public class CloudflareLogsBatchConsumer {

    private final ExecutorService executorService;
    private final Consumer<List<Map<String, Object>>> batchInsertFunction;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String topic;

    public CloudflareLogsBatchConsumer(ExecutorService executorService,
                                       Consumer<List<Map<String, Object>>> batchInsertFunction,
                                       String topic) {
        this.executorService = executorService;
        this.batchInsertFunction = batchInsertFunction;
        this.topic = topic;
    }

    public void consumeRecords(List<ConsumerRecord<String, String>> records) {
        if (records == null || records.isEmpty()) return;

        // 提取消息和 traceId
        List<String> messages = new ArrayList<>();
        Map<String, String> traceIds = new HashMap<>();
        for (ConsumerRecord<String, String> record : records) {
            messages.add(record.value());
            Header header = record.headers().lastHeader("traceId");
            String traceId = header != null ? new String(header.value(), StandardCharsets.UTF_8) : "UNKNOWN";
            traceIds.put(record.value(), traceId);
        }

        // 动态 batchSize
        int batchSize = ThreadPoolUtils.calculateSmartBatchSize(executorService, messages.size(), 10, 200);

        // 使用第一条消息 traceId 作为整个 Kafka 批次 traceId
        String batchTraceId = messages.isEmpty() ? "UNKNOWN" : traceIds.getOrDefault(messages.get(0), "UNKNOWN");

        // ✅ Kafka 批次开始日志
        log.info("[traceId={}] 🧵✅ [{}] Starting processing Kafka batch | totalMessages={} | calculatedBatchSize={}",
                batchTraceId, topic, messages.size(), batchSize);

        // 拆分 batch
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += batchSize) {
            batches.add(messages.subList(i, Math.min(i + batchSize, messages.size())));
        }

        // 多线程并发处理 batch
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<String> batch : batches) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (String msg : batch) {
                    String traceId = traceIds.getOrDefault(msg, "UNKNOWN");
                    MDC.put("traceId", traceId);
                    try {
                        processMessage(msg, batchTraceId);
                    } finally {
                        MDC.remove("traceId");
                    }
                }
            }, executorService);
            futures.add(future);
        }

        // 等待所有 batch 完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // ✅ Kafka 批次完成日志（只打印一次）
        log.info("[traceId={}] ✅ [{}] Finished processing entire Kafka batch | totalMessages={}",
                batchTraceId, topic, messages.size());

        // 打印线程池状态
        if (executorService instanceof ThreadPoolExecutor threadPoolExecutor) {
            ThreadPoolUtils.logThreadPoolStatus(threadPoolExecutor, topic);
        }
    }

    /**
     * 单条消息处理逻辑
     */
    private void processMessage(String message, String batchTraceId) {
        if (message == null || message.trim().isEmpty()) return;

        try {
            if (message.trim().startsWith("{") && message.trim().endsWith("}")) {
                Map<String, Object> original = objectMapper.readValue(message, Map.class);
                Map<String, Object> flattened = CloudflareLogUtils.flattenAndBuildParams(original);
                try {
                    batchInsertFunction.accept(Collections.singletonList(flattened));
                } catch (Exception e) {
                    // 只打印简短 SQL 插入失败信息，不打印堆栈
//                    log.error("[traceId={}] SQL insert failed", batchTraceId);
                    ExceptionUtils.logSimple(batchTraceId, "SQL insert failed");

                }
            }
        } catch (Exception e) {
            // 解析失败也只打印简单日志
            ExceptionUtils.logSimple(batchTraceId, "Failed to parse message");
        }
    }
}
