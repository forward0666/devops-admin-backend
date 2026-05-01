package com.backend.bot.controller;

import com.backend.bot.entity.BotMenuEntity;
import com.backend.bot.service.BotMenuService;
import com.backend.bot.util.LogUtils;
import com.backend.bot.vo.BotMenuVo;
import network.HttpResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class BotMenuController {

    private final BotMenuService botMenuService;

    @GetMapping("/bot/{botName}")
    public Mono<ResponseEntity<Map<String, Object>>> getMenusByBot(@PathVariable String botName) {
        return Mono.deferContextual(ctx -> {
            LogUtils.syncTraceIdToMDC(ctx);
            return botMenuService.findByBotName(botName)
                    .map(BotMenuVo::fromEntity)
                    .collectList()
                    .map(list -> HttpResponseUtils.ok(Map.of("menus", list)));
        }).doFinally(LogUtils::clearMDC);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getMenu(@PathVariable Long id) {
        return Mono.deferContextual(ctx -> {
            LogUtils.syncTraceIdToMDC(ctx);
            return botMenuService.findById(id)
                    .map(vo -> HttpResponseUtils.ok(Map.of("menu", BotMenuVo.fromEntity(vo))))
                    .defaultIfEmpty(HttpResponseUtils.badRequest("Menu not found"));
        }).doFinally(LogUtils::clearMDC);
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createMenu(@RequestBody Mono<BotMenuEntity> entityMono) {
        return Mono.deferContextual(ctx -> {
            LogUtils.syncTraceIdToMDC(ctx);
            return entityMono
                    .doOnNext(e -> {
                        e.setCreatedAt(LocalDateTime.now());
                        e.setUpdatedAt(LocalDateTime.now());
                        if (e.getSortOrder() == null) e.setSortOrder(0);
                    })
                    .flatMap(botMenuService::save)
                    .map(saved -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("menu", BotMenuVo.fromEntity(saved));
                        return data;
                    })
                    .flatMap(data -> Mono.just(ResponseEntity.status(201).body(data)));
        }).doFinally(LogUtils::clearMDC);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateMenu(@PathVariable Long id, @RequestBody Mono<BotMenuEntity> entityMono) {
        return Mono.deferContextual(ctx -> {
            LogUtils.syncTraceIdToMDC(ctx);
            return botMenuService.findById(id)
                    .flatMap(existing -> entityMono.map(e -> {
                        existing.setTitle(e.getTitle());
                        existing.setButtons(e.getButtons());
                        existing.setSortOrder(e.getSortOrder());
                        existing.setMenuKey(e.getMenuKey());
                        existing.setMenuLevel(e.getMenuLevel());
                        existing.setParentId(e.getParentId());
                        existing.setBotName(e.getBotName());
                        existing.setUpdatedAt(LocalDateTime.now());
                        return existing;
                    }))
                    .flatMap(botMenuService::save)
                    .map(saved -> HttpResponseUtils.ok(Map.of("menu", BotMenuVo.fromEntity(saved))))
                    .defaultIfEmpty(HttpResponseUtils.badRequest("Menu not found"));
        }).doFinally(LogUtils::clearMDC);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteMenu(@PathVariable Long id) {
        return Mono.deferContextual(ctx -> {
            LogUtils.syncTraceIdToMDC(ctx);
            return botMenuService.findById(id)
                    .flatMap(existing -> botMenuService.deleteById(id).thenReturn(existing))
                    .map(deleted -> HttpResponseUtils.ok(Map.of()))
                    .defaultIfEmpty(HttpResponseUtils.badRequest("Menu not found"));
        }).doFinally(LogUtils::clearMDC);
    }
}
