package com.backend.bot.controller;

import com.backend.bot.entity.BotMenuEntity;
import com.backend.bot.service.BotMenuService;
import com.backend.bot.util.LogUtils;
import com.backend.bot.vo.BotMenuVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class BotMenuController {

    private final BotMenuService botMenuService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Normalize buttons JSON: ensure valid UTF-8 encoding for MySQL JSON column
     */
    private String normalizeButtons(String buttons) {
        if (buttons == null || buttons.isBlank()) return buttons;
        try {
            // Remove control characters (newline, tab, etc.) that break JSON parsing
            String cleaned = buttons.replaceAll("[\\x00-\\x1F]", "");
            Object parsed = objectMapper.readValue(cleaned, Object.class);
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception e) {
            log.warn("Invalid buttons JSON: {}", e.getMessage());
            return buttons;
        }
    }

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> getAllMenus(@RequestParam(required = false) String botType) {
        return Mono.deferContextual(ctx -> {
            LogUtils.syncTraceIdToMDC(ctx);
            if (botType != null) {
                return botMenuService.findAll()
                        .filter(e -> botType.equals(e.getBotType()))
                        .map(BotMenuVo::fromEntity)
                        .collectList()
                        .map(list -> HttpResponseUtils.ok(Map.of("menus", list)));
            }
            return botMenuService.findAll()
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
                        if (e.getSortOrder() == null) e.setSortOrder(0);
                        e.setButtons(normalizeButtons(e.getButtons()));
                    })
                    .flatMap(newMenu -> botMenuService.save(newMenu)
                            .onErrorResume(ex -> {
                                boolean isDuplicate = false;
                                Throwable t = ex;
                                while (t != null) {
                                    if (t instanceof org.springframework.dao.DuplicateKeyException
                                            || (t.getMessage() != null && t.getMessage().contains("Duplicate entry"))) {
                                        isDuplicate = true;
                                        break;
                                    }
                                    t = t.getCause();
                                }
                                if (!isDuplicate) return Mono.error(ex);

                                log.warn("Menu key conflict, attempting update: botType={}, menuKey={}", newMenu.getBotType(), newMenu.getMenuKey());
                                // Key exists → find and update
                                return botMenuService.findByBotTypeAndMenuKey(newMenu.getBotType(), newMenu.getMenuKey())
                                        .flatMap(existing -> {
                                            existing.setTitle(newMenu.getTitle());
                                            existing.setButtons(normalizeButtons(newMenu.getButtons()));
                                            existing.setSortOrder(newMenu.getSortOrder());
                                            existing.setMenuLevel(newMenu.getMenuLevel());
                                            existing.setParentId(newMenu.getParentId());
                                            existing.setUpdatedAt(LocalDateTime.now());
                                            return botMenuService.save(existing);
                                        })
                                        .switchIfEmpty(Mono.error(new RuntimeException("Menu key exists but could not be found: " + newMenu.getMenuKey())));
                            })
                    )
                    .flatMap(saved -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("menu", BotMenuVo.fromEntity(saved));
                        return botMenuService.deleteCacheByBotType(saved.getBotType())
                                .thenReturn(HttpResponseUtils.created("Menu saved", data));
                    });
        }).doFinally(LogUtils::clearMDC);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateMenu(@PathVariable Long id, @RequestBody Mono<BotMenuEntity> entityMono) {
        return Mono.deferContextual(ctx -> {
            LogUtils.syncTraceIdToMDC(ctx);
            return botMenuService.findById(id)
                    .flatMap(existing -> entityMono.map(e -> {
                        existing.setTitle(e.getTitle());
                        existing.setButtons(normalizeButtons(e.getButtons()));
                        existing.setSortOrder(e.getSortOrder());
                        existing.setMenuKey(e.getMenuKey());
                        existing.setMenuLevel(e.getMenuLevel());
                        existing.setParentId(e.getParentId());
                        existing.setBotType(e.getBotType());
                        existing.setUpdatedAt(LocalDateTime.now());
                        return existing;
                    }))
                    .flatMap(botMenuService::save)
                    .flatMap(saved -> botMenuService.deleteCacheByBotType(saved.getBotType())
                            .thenReturn(HttpResponseUtils.ok(Map.of("menu", BotMenuVo.fromEntity(saved)))))
                    .defaultIfEmpty(HttpResponseUtils.badRequest("Menu not found"));
        }).doFinally(LogUtils::clearMDC);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteMenu(@PathVariable Long id) {
        return Mono.deferContextual(ctx -> {
            LogUtils.syncTraceIdToMDC(ctx);
            return botMenuService.findById(id)
                    .flatMap(existing -> botMenuService.deleteById(id).thenReturn(existing))
                    .flatMap(deleted -> botMenuService.deleteCacheByBotType(deleted.getBotType())
                            .thenReturn(HttpResponseUtils.ok(Map.of())))
                    .defaultIfEmpty(HttpResponseUtils.badRequest("Menu not found"));
        }).doFinally(LogUtils::clearMDC);
    }
}
