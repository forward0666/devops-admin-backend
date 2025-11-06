package com.backend.bigdata.consumer;

import com.backend.bigdata.consumer.CloudflareLogsBatchConsumer;
import com.backend.bigdata.mapper.CloudflareLogsMapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
public class KafkaConsumerHttpRequestsForU8App {
    private final CloudflareLogsMapper cloudflareLogsMapper;
    private final ExecutorService executorService;
    private final CloudflareLogsBatchConsumer batchConsumer;

    @Autowired
    public KafkaConsumerHttpRequestsForU8App(
            CloudflareLogsMapper cloudflareLogsMapper,
            ExecutorService executorService,
            @Value("${cloudflare.topic:cloudflare_logs_http_requests_u8_app}") String topic) {

        this.cloudflareLogsMapper = cloudflareLogsMapper;
        this.executorService = executorService;

        // 初始化 batchConsumer
        this.batchConsumer = new CloudflareLogsBatchConsumer(
                executorService,
                cloudflareLogsMapper::batchInsertCloudflareHttpLogsForU8App,
                topic
        );
    }

    @KafkaListener(
            topics = "${cloudflare.topic:cloudflare_logs_http_requests_u8_app}",
            groupId = "cloudflare_logs_http_requests",
            containerFactory = "kafkaBatchListenerContainerFactory"
    )
    public void consume(List<String> messages) {
        // ✅ 为每批 Kafka 消息生成全链路 traceId
        String traceId = java.util.UUID.randomUUID().toString();
        batchConsumer.consume(messages, traceId);
    }
}
