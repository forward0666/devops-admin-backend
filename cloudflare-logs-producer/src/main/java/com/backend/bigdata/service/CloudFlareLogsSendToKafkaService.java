package com.backend.bigdata.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cloudflare日志发送到Kafka服务
 * 适用于 Spring Kafka 2.8+（KafkaTemplate.send 返回 CompletableFuture）
 */
@Slf4j
@Service
public class CloudFlareLogsSendToKafkaService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 发送消息到 Kafka
     *
     * @param topic        Kafka topic
     * @param message      消息内容
     * @param firstSuccess 原子标志，第一次成功时打印完成日志
     */
    public void sendMessage(String topic, String message, AtomicBoolean firstSuccess) {
        // ✅ 从 MDC 获取 traceId
        String traceId = MDC.get("traceId");

        try {
            // ✅ 构建 ProducerRecord，并把 traceId 写入 header
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
            if (traceId != null) {
                record.headers().add(new RecordHeader("traceId", traceId.getBytes(StandardCharsets.UTF_8)));
            }

            // ✅ 发送消息，返回 CompletableFuture
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);

            // ✅ 异步回调
            future.whenComplete((result, ex) -> {
                if (traceId != null) MDC.put("traceId", traceId);
                try {
                    if (ex != null) {
                        log.error("[traceId={}] ❌ Kafka send failed: {}", traceId, ex.getMessage(), ex);
                    } else {
                        if (firstSuccess != null && firstSuccess.compareAndSet(false, true)) {
//                            log.info("[traceId={}] ✅ Kafka messages sent finished: {}", traceId, topic);
                        }
                    }
                } finally {
                    if (traceId != null) MDC.remove("traceId");
                }
            });

        } catch (Exception e) {
            log.error("[traceId={}] ❌ Kafka send exception: {}", traceId, e.getMessage(), e);
        }
    }
}
