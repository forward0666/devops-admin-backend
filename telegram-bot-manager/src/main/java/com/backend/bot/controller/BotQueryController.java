package com.backend.bot.controller;

import com.backend.bot.enums.BotType;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.vo.BotVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/bots")
public class BotQueryController {

    private final BotCoreService botCoreService;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> getAllBots() {
        return botCoreService.findAll()
                .collectList()
                .map(bots -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("bots", bots);
                    return HttpResponseUtils.ok(data);
                });
    }

    @GetMapping("/{name}")
    public Mono<ResponseEntity<Map<String, Object>>> getBotByName(@PathVariable String name) {
        return botCoreService.findByBotName(name)
                .map(entity -> {
                    BotVo botVo = botCoreService.convertToVo(entity);
                    Map<String, Object> data = new HashMap<>();
                    data.put("bot", botVo);
                    return HttpResponseUtils.ok(data);
                })
                .defaultIfEmpty(HttpResponseUtils.notFound("Bot not found: " + name));
    }

    @PutMapping("/{name}/status")
    public Mono<ResponseEntity<Map<String, Object>>> updateBotStatus(
            @PathVariable String name,
            @RequestParam Integer status) {

        if (status != 0 && status != 1) {
            return Mono.just(HttpResponseUtils.badRequest("Status must be 0 or 1"));
        }

        return botCoreService.updateBotStatusByName(name, status)
                .map(updated -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("botName", name);
                    data.put("status", status);
                    return HttpResponseUtils.ok(data);
                })
                .defaultIfEmpty(HttpResponseUtils.notFound("Bot not found: " + name));
    }

    @GetMapping("/{name}/status")
    public Mono<ResponseEntity<Map<String, Object>>> getBotStatus(@PathVariable String name) {
        return botCoreService.findByBotName(name)
                .map(entity -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("botName", name);
                    data.put("status", entity.getStatus());
                    return HttpResponseUtils.ok(data);
                })
                .defaultIfEmpty(HttpResponseUtils.notFound("Bot not found: " + name));
    }

    @DeleteMapping("/{name}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteBot(@PathVariable String name) {
        return botCoreService.deleteByBotName(name)
                .map(deleted -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("botName", name);
                    if (Boolean.TRUE.equals(deleted)) {
                        return HttpResponseUtils.ok(data);
                    } else {
                        return HttpResponseUtils.notFound("Bot not found: " + name);
                    }
                })
                .onErrorResume(e -> {
                    log.error("❌ Delete bot failed: {}", name, e);
                    return Mono.just(HttpResponseUtils.internalError("Delete failed: " + e.getMessage()));
                });
    }

    /**
     * 更新 Bot 信息（类型、状态）
     */
    @PutMapping("/{name}")
    public Mono<ResponseEntity<Map<String, Object>>> updateBot(
            @PathVariable String name,
            @RequestBody Map<String, Object> body) {

        log.info("✏️ Update bot request: name={}, body={}", name, body);

        return botCoreService.findByBotName(name)
                .flatMap(bot -> {
                    if (body.containsKey("botType")) {
                        try {
                            bot.setBotType(BotType.valueOf((String) body.get("botType")));
                        } catch (IllegalArgumentException e) {
                            return Mono.just(HttpResponseUtils.badRequest("Invalid bot type: " + body.get("botType")));
                        }
                    }
                    if (body.containsKey("token") && body.get("token") != null && !((String) body.get("token")).isBlank()) {
                        bot.setBotToken((String) body.get("token"));
                    }
                    Integer status = body.containsKey("status") ? (Integer) body.get("status") : null;
                    if (status != null && status != 0 && status != 1) {
                        return Mono.just(HttpResponseUtils.badRequest("Status must be 0 or 1"));
                    }
                    if (status != null) {
                        bot.setStatus(status);
                    }
                    bot.setUpdatedAt(java.time.LocalDateTime.now());
                    return botCoreService.saveBot(bot)
                            .map(saved -> {
                                Map<String, Object> data = new HashMap<>();
                                data.put("bot", botCoreService.convertToVo(saved));
                                return HttpResponseUtils.ok(data);
                            });
                })
                .defaultIfEmpty(HttpResponseUtils.notFound("Bot not found: " + name))
                .onErrorResume(e -> {
                    log.error("❌ Update bot failed: {}", name, e);
                    return Mono.just(HttpResponseUtils.internalError("Update failed: " + e.getMessage()));
                });
    }

}
