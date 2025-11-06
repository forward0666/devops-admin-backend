package com.backend.bigdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

import java.util.concurrent.ExecutorService;

/**
 * Kafka批量消费者配置类
 *
 * 功能说明：
 * 配置Kafka批量消费者工厂，用于高效处理大量Cloudflare日志消息
 *
 * 核心特性：
 * - 启用批量监听模式，一次处理多条消息
 * - 配置批量确认模式，提高消费效率
 * - 支持高吞吐量的日志数据处理
 *
 * 配置参数：
 * - batchListener: true - 启用批量消息处理
 * - ackMode: BATCH - 批量处理完成后才提交offset
 * - consumerFactory: 注入已配置的消费者工厂
 *
 * 性能优化：
 * - 批量处理减少数据库IO操作
 * - 批量提交提高Kafka消费效率
 * - 适合高吞吐量的日志数据场景
 *
 * 使用场景：
 * - Cloudflare HTTP请求日志的批量消费
 * - 需要高性能处理的日志数据管道
 * - 大数据量的实时数据处理
 */
@EnableKafka
@Configuration
public class KafkaBatchConsumerConfig {

    /**
     * 创建Kafka批量监听器容器工厂
     *
     * 功能说明：
     * 配置支持批量处理的Kafka监听器工厂
     *
     * 配置说明：
     * 1. 设置消费者工厂
     * 2. 启用批量监听模式
     * 3. 配置批量确认模式
     *
     * 性能特性：
     * - 批量处理：一次处理多条消息，减少IO开销
     * - 批量确认：处理完一批消息后才提交offset
     * - 并发消费：支持多个消费者实例并行处理
     *
     * @param consumerFactory Kafka消费者工厂，提供消费者配置
     * @return 配置好的批量监听器容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaBatchListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory
            ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
//        factory.setConcurrency(n)
        factory.getContainerProperties().setAckMode(AckMode.BATCH);
        return factory;
    }
}