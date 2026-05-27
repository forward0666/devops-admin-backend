package com.backend.bot.service;

import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotMenuEntity;
import com.backend.bot.repository.BotMenuRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotMenuService {

    private final BotMenuRepository botMenuRepository;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final Duration MENU_CACHE_TTL = Duration.ofSeconds(300);

    public Flux<BotMenuEntity> findAll() {
        return botMenuRepository.findAll();
    }

    public Mono<BotMenuEntity> findById(Long id) {
        return botMenuRepository.findById(id);
    }

    public Mono<BotMenuEntity> findByBotTypeAndMenuKey(String botType, String menuKey) {
        return botMenuRepository.findByBotTypeAndMenuKey(botType, menuKey);
    }

    public Mono<BotMenuEntity> save(BotMenuEntity entity) {
        return botMenuRepository.save(entity)
                .doOnNext(saved -> evictMenuCache(saved.getBotType()));
    }

    public Mono<Void> deleteById(Long id) {
        return botMenuRepository.findById(id)
                .flatMap(e -> botMenuRepository.deleteById(id).then(Mono.just(e)))
                .doOnNext(e -> evictMenuCache(e.getBotType()))
                .then();
    }

    public Mono<InlineKeyboardMarkupDto> findKeyboardByBotTypeAndMenuKey(String botType, String menuKey) {
        String cacheKey = "bot:menu:" + botType + ":key:" + menuKey;
        log.info("🔍 [MenuService] findKeyboard: botType={}, menuKey={}", botType, menuKey);

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> {
                    try {
                        InlineKeyboardMarkupDto markup = objectMapper.readValue(cached, InlineKeyboardMarkupDto.class);
                        log.debug("Cache hit for key={}", cacheKey);
                        return Mono.just(markup);
                    } catch (Exception e) {
                        log.warn("Cache deserialization failed for key={}, will query DB", cacheKey);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.defer(() -> botMenuRepository.findByBotTypeAndMenuKey(botType, menuKey)
                        .doOnNext(entity -> {
                            String btns = entity.getButtons() != null
                                    ? entity.getButtons().substring(0, Math.min(80, entity.getButtons().length()))
                                    : "null";
                            log.info("🔍 [MenuService] Found entity: id={}, title={}, buttons={}", entity.getId(), entity.getTitle(), btns);
                        })
                        .map(this::entityToKeyboard)
                        .doOnNext(markup -> {
                            try {
                                String json = objectMapper.writeValueAsString(markup);
                                redisTemplate.opsForValue().set(cacheKey, json, MENU_CACHE_TTL).subscribe();
                            } catch (Exception e) {
                                log.warn("Failed to cache menu key={}", cacheKey, e);
                            }
                        })
                ))
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("🔍 [MenuService] No menu found for botType={} menuKey={}", botType, menuKey);
                    return Mono.empty();
                }));
    }

    public Mono<InlineKeyboardMarkupDto> findMainMenuByBotType(String botType, int menuLevel) {
        String cacheKey = "bot:menu:" + botType + ":main:" + menuLevel;
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> {
                    try {
                        InlineKeyboardMarkupDto markup = objectMapper.readValue(cached, InlineKeyboardMarkupDto.class);
                        log.debug("Cache hit for key={}", cacheKey);
                        return Mono.just(markup);
                    } catch (Exception e) {
                        log.warn("Cache deserialization failed for key={}, will query DB", cacheKey);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Querying menu by botType={} level={}", botType, menuLevel);
                    return botMenuRepository.findByBotTypeAndMenuLevel(botType, menuLevel)
                            .map(this::entityToKeyboard)
                            .doOnNext(markup -> {
                                try {
                                    String json = objectMapper.writeValueAsString(markup);
                                    redisTemplate.opsForValue().set(cacheKey, json, MENU_CACHE_TTL).subscribe();
                                } catch (Exception e) {
                                    log.warn("Failed to cache menu key={}", cacheKey, e);
                                }
                            });
                }));
    }

    public Mono<Void> deleteCacheByBotType(String botType) {
        return redisTemplate.keys("bot:menu:" + botType + ":*")
                .flatMap(redisTemplate::delete)
                .then();
    }

    private void evictMenuCache(String botType) {
        redisTemplate.keys("bot:menu:" + botType + ":*")
                .flatMap(redisTemplate::delete)
                .subscribe();
    }

    private InlineKeyboardMarkupDto entityToKeyboard(BotMenuEntity entity) {
        try {
            InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
            if (entity.getButtons() == null || entity.getButtons().isBlank()) {
                return markup;
            }
            List<List<Map<String, String>>> rows = objectMapper.readValue(entity.getButtons(), new TypeReference<>() {});
            for (List<Map<String, String>> row : rows) {
                if (row == null || row.isEmpty()) {
                    markup.addRow();
                } else {
                    InlineKeyboardButtonDto[] buttons = row.stream()
                            .filter(b -> b != null && b.containsKey("text") && b.containsKey("callbackData"))
                            .map(b -> new InlineKeyboardButtonDto(b.get("text"), b.get("callbackData")))
                            .toArray(InlineKeyboardButtonDto[]::new);
                    if (buttons.length > 0) {
                        markup.addRow(buttons);
                    } else {
                        markup.addRow();
                    }
                }
            }
            return markup;
        } catch (Exception e) {
            log.error("Failed to parse buttons JSON for menu id={}: {}", entity.getId(), entity.getButtons(), e);
            return new InlineKeyboardMarkupDto();
        }
    }
}
