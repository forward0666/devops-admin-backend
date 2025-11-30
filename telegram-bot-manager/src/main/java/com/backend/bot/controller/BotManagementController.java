package com.backend.bot.controller;

import com.backend.bot.dto.BotRegisterDto;
import com.backend.bot.service.BotCoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotManagementController {

    private final BotCoreService botCoreService;

    /**
     * 注册新的 Bot 到系统，并初始化其 Webhook。
     */
    @PostMapping("/addBot")
    public Mono<ResponseEntity<Map<String, Object>>> addBot(
            @Valid @RequestBody Mono<BotRegisterDto> dtoMono) {

        return dtoMono
                .doOnNext(dto -> log.info("✅ Received request to register bot: {}", dto.getBotUsername()))
                .flatMap(botCoreService::registerNewBot)
                .map(botVo -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("bot", botVo);
                    return HttpResponseUtils.created("✅ Bot 注册成功并已设置 Webhook");
                })
                .onErrorResume(e -> {
                    log.error("❌ Bot registration failed", e);
                    return Mono.just(HttpResponseUtils.internalError("❌ Bot 注册失败: " + e.getMessage()));
                });
    }
}