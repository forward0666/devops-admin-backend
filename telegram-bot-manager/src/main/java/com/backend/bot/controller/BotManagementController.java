package com.backend.bot.controller;

import com.backend.bot.dto.BotRegisterDto;
import com.backend.bot.service.BotCoreService;
import filter.TraceIdFilter; // 🌟 导入 TraceIdFilter
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.slf4j.MDC; // 🌟 导入 MDC
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.Optional; // 🌟 导入 Optional

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotManagementController {

    private final BotCoreService botCoreService;
    // ❌ 移除这个字段，它在 WebFlux 中是无效的，必须在 Mono 链中获取。
    // String traceId = MDC.get("traceId");

    // 辅助方法：获取 Trace ID 并设置 MDC
    private Mono<Void> setMdcFromContext() {
        return Mono.deferContextual(contextView -> {
            Optional<Object> traceIdOpt = contextView.getOrEmpty(TraceIdFilter.CONTEXT_KEY_TRACE_ID);
            if (traceIdOpt.isPresent()) {
                String traceId = traceIdOpt.get().toString();
                MDC.put("traceId", traceId);
            }
            return Mono.empty();
        });
    }

    /**
     * 注册新的 Bot 到系统，并初始化其 Webhook。
     */
    @PostMapping("/addBot")
    public Mono<ResponseEntity<Map<String, Object>>> addBot(
            @Valid @RequestBody Mono<BotRegisterDto> dtoMono) {


        return setMdcFromContext() // 🌟 步骤 1: 设置 MDC
                .then(dtoMono)
                // 1. 记录请求日志 (现在会自动包含 traceId)
                .doOnNext(dto -> log.info("✅ Received request to register bot: {}", dto.getBotUsername()))

                .flatMap(botCoreService::registerNewBot)

                .map(botVo -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("bot", botVo);
                    return HttpResponseUtils.created("✅ Bot 注册成功并已设置 Webhook", data);
                })

                .onErrorResume(e -> {
                    log.error("❌ Bot registration failed", e);
                    return Mono.just(HttpResponseUtils.internalError("❌ Bot 注册失败: " + e.getMessage()));
                })
                .doFinally(signal -> MDC.clear()); // 🌟 步骤 2: 在链结束时清除 MDC
    }
}