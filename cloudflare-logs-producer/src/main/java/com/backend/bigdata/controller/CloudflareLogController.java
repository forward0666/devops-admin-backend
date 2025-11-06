package com.backend.bigdata.controller;

import com.backend.bigdata.service.CloudFlareLogsSendToKafkaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import static compression.GzipUtils.isGzipFormat;
import static network.HttpResponseUtils.*;
import static network.ThreadPoolUtils.calculateSmartBatchSize;

@Slf4j
@RestController
@RequestMapping("/cloudflare_logs")
public class CloudflareLogController {

    @Value("${cloudflare.allowed-projects}")
    private String allowedProjects;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private CloudFlareLogsSendToKafkaService cloudFlareLogsSendToKafkaService;

    @PostMapping(
            value = "/{project}",
            consumes = {"application/gzip", "application/json", "text/plain"},
            produces = "application/json"
    )
    public Map<String, Object> receiveLogs(
            @PathVariable("project") String project,
            @RequestBody byte[] compressedBody,
            @RequestHeader(value = "Content-Encoding", required = false, defaultValue = "identity") String encoding) {

        // ✅ 生成 traceId，用于全链路追踪
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        try {
            // 🧩 校验项目白名单
            boolean isAllowed = Arrays.stream(allowedProjects.split(","))
                    .anyMatch(p -> p.trim().equals(project));
            if (!isAllowed) {
                log.warn("❌ Rejected project not in whitelist: {} | traceId={}", project, traceId);
                return badRequest("Project not allowed").getBody();
            }

            log.info("📩 Received body ({} bytes) | project={} | encoding={} | traceId={}",
                    compressedBody.length, project, encoding, traceId);

            // 🗜️ 解压内容
            String decompressed;
            if ("gzip".equalsIgnoreCase(encoding)) {
                if (!isGzipFormat(compressedBody)) {
                    return badRequest("Invalid GZIP data").getBody();
                }
                try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressedBody))) {
                    decompressed = new String(gis.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                decompressed = new String(compressedBody, StandardCharsets.UTF_8);
            }

            // 🪣 按行拆分 JSON logs
            List<String> lines = Arrays.stream(decompressed.split("\n"))
                    .filter(line -> !line.isBlank())
                    .toList();
            int totalLines = lines.size();
            log.info("🧾 Parsed {} JSON lines from project '{}' | traceId={}", totalLines, project, traceId);

            if (totalLines == 0) {
                return badRequest("No valid lines found").getBody();
            }

            String topic = "cloudflare_logs_" + project;
            AtomicBoolean firstSuccess = new AtomicBoolean(false);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);

            // 🧮 动态计算 batchSize
            int batchSize = calculateSmartBatchSize(executorService, totalLines, 10, 200);
            log.info("🛠 Calculated dynamic batchSize={} | traceId={}", batchSize, traceId);

            // 🧩 构建批次列表
            List<List<String>> batches = new ArrayList<>();
            for (int i = 0; i < totalLines; i += batchSize) {
                batches.add(lines.subList(i, Math.min(i + batchSize, totalLines)));
            }

            log.info("🚀 Starting concurrent Kafka send with {} batches | traceId={}", batches.size(), traceId);

            // 🚀 并发发送到 Kafka
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (List<String> batch : batches) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    MDC.put("traceId", traceId); // ✅ 保证每个线程日志都有 traceId
                    int batchSent = 0;
                    for (String line : batch) {
                        try {
                            objectMapper.readTree(line); // 校验 JSON
                            cloudFlareLogsSendToKafkaService.sendMessage(topic, line, firstSuccess);
                            successCount.incrementAndGet();
                            batchSent++;
                        } catch (Exception e) {
                            failedCount.incrementAndGet();
                            log.warn("⚠️ Invalid JSON skipped: {} | traceId={}", e.getMessage(), traceId);
                        }
                    }
                    log.info("🧵 [{}] finished sending {} messages → topic '{}' | traceId={}",
                            Thread.currentThread().getName(), batchSent, topic, traceId);
                    MDC.remove("traceId");
                }, executorService);
                futures.add(future);
            }

            // ⏳ 等待所有线程完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            log.info("✅ Kafka send finished | traceId={} | totalLines={} | success={} | failed={}",
                    traceId, totalLines, successCount.get(), failedCount.get());

            // ✅ 返回响应
            Map<String, Object> response = new HashMap<>();
            response.put("topic", topic);
            response.put("traceId", traceId);
            response.put("totalLines", totalLines);
            response.put("successLines", successCount.get());
            response.put("failedLines", failedCount.get());
            response.put("timestamp", OffsetDateTime.now().toString());
            return ok(response).getBody();

        } catch (Exception e) {
            log.error("❌ Kafka send failed | traceId={} | error={}", traceId, e.getMessage(), e);
            return internalError("Kafka send failed").getBody();
        } finally {
            MDC.remove("traceId"); // ✅ 清理 MDC 避免内存泄漏
        }
    }
}
