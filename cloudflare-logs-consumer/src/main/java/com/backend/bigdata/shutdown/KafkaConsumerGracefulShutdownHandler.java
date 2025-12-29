package com.backend.bigdata.shutdown;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Kafka Consumer 优雅关闭处理器
 *
 * <p>确保应用关闭时，正在处理的消息消费完成，避免重复或丢失。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.role", havingValue = "consumer")
public class KafkaConsumerGracefulShutdownHandler {

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @PreDestroy
    public void onShutdown() {
        log.info("🚦 Application shutting down. Stopping Kafka listeners gracefully...");

        Collection<MessageListenerContainer> containers = kafkaListenerEndpointRegistry.getListenerContainers();
        for (MessageListenerContainer container : containers) {
            // 停止容器接收新消息，但会处理完已拉取的消息
            container.stop(() -> log.info("✅ Kafka listener [{}] stopped.", container.getListenerId()));
        }

        log.info("✅ All Kafka listeners stopped gracefully.");
    }
}
