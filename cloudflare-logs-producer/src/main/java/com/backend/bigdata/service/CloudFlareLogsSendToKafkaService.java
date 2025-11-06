package com.backend.bigdata.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cloudflare日志发送到Kafka服务
 * 中文注释：处理将Cloudflare日志数据发送到Kafka消息队列的服务类
 * 使用Spring KafkaTemplate进行异步消息发送，支持成功和失败回调
 */
@Slf4j
@Service
public class CloudFlareLogsSendToKafkaService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void sendMessage(String topic, String message, AtomicBoolean firstSuccess) {
        kafkaTemplate.send(topic, message)
                .thenAccept(result -> {
                    if (firstSuccess != null && firstSuccess.compareAndSet(false, true)) {
                        log.info("✅ Kafka messages sent finished: {}", topic);
                    }
                })
                .exceptionally(ex -> {
                    log.error("❌ Kafka send failed: {}", ex.getMessage(), ex);
                    return null;
                });
    }


}


