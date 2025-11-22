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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class CloudFlareLogsSendToKafkaService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void sendMessage(String topic, String message, AtomicBoolean firstSuccess) {

        // 当前线程 MDC 的 traceId
        String traceId = MDC.get("traceId");

        // ⚠️ 完整复制 MDC 副本，给异步线程继承
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);

            // traceId 写入 Kafka header
            if (traceId != null) {
                record.headers().add(
                        new RecordHeader("traceId", traceId.getBytes(StandardCharsets.UTF_8))
                );
            }

            // 发送 Kafka 异步
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);

            future.whenComplete((result, ex) -> {

                // 回调线程恢复 MDC
                if (contextMap != null) MDC.setContextMap(contextMap);

                try {
                    if (ex != null) {
                        log.error("[traceId={}] ❌ Kafka send failed: {}", traceId, ex.getMessage(), ex);
                    } else if (firstSuccess != null && firstSuccess.compareAndSet(false, true)) {
                        // 你之前注释掉的日志
                        // log.info("[traceId={}] ✅ Kafka messages sent finished: {}", traceId, topic);
                    }
                } finally {
                    MDC.clear();
                }
            });

        } catch (Exception e) {
            log.error("[traceId={}] ❌ Kafka send exception: {}", traceId, e.getMessage(), e);
        }
    }
}
