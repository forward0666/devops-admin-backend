package com.backend.bigdata.shutdown;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka Producer 优雅关闭处理器
 *
 * <p>确保应用关闭时，Kafka producer 缓存的消息被 flush，避免数据丢失。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.role", havingValue = "producer")
public class KafkaProducerGracefulShutdownHandler {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @PreDestroy
    public void onShutdown() {
        log.info("🚦 Application shutting down. Flushing Kafka producer...");
        kafkaTemplate.flush();
        log.info("✅ Kafka producer flush complete.");
    }
}
