package com.backend.bot.controller;

import com.backend.bot.dto.BotRegisterDto;
import com.backend.bot.service.BotCoreService;
// import filter.TraceIdFilter; // 🌟 移除：不再直接使用 TraceIdFilter
// import org.slf4j.MDC; // 🌟 移除：不再直接操作 MDC.put/clear
import com.backend.bot.util.LogUtils; // 🌟 导入 LogUtils
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
// import java.util.Optional; // 🌟 移除：不再直接使用 Optional

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotManagementController {

    private final BotCoreService botCoreService;
    // ❌ 移除重复的私有辅助方法 setMdcFromContext()

    /**
     * 注册新的 Bot 到系统，并初始化其 Webhook。
     */
    @PostMapping("/addBot")
    public Mono<ResponseEntity<Map<String, Object>>> addBot(
            @Valid @RequestBody Mono<BotRegisterDto> dtoMono) {

        // 🌟 步骤 1: 使用 LogUtils 抽象的 MDC 设置方法
        return LogUtils.setMdcFromContext()
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
                // 🌟 步骤 2: 使用 LogUtils 抽象的 MDC 清理方法
                .doFinally(LogUtils::clearMDC);
    }
}