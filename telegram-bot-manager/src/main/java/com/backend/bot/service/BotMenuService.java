package com.backend.bot.service;

import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotMenuEntity;
import com.backend.bot.repository.BotMenuRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotMenuService {

    private final BotMenuRepository botMenuRepository;
    private final ObjectMapper objectMapper;

    public Flux<BotMenuEntity> findByBotName(String botName) {
        return botMenuRepository.findByBotNameOrderByLevelAndSort(botName);
    }

    public Mono<BotMenuEntity> findById(Long id) {
        return botMenuRepository.findById(id);
    }

    public Mono<BotMenuEntity> save(BotMenuEntity entity) {
        return botMenuRepository.save(entity);
    }

    public Mono<Void> deleteById(Long id) {
        return botMenuRepository.deleteById(id);
    }

    /**
     * 根据 botName + menuKey 查找菜单并转换为 InlineKeyboardMarkupDto
     * 用于 MenuType.createDynamicKeyboard() 的数据库查询
     */
    public Mono<InlineKeyboardMarkupDto> findKeyboardByBotNameAndMenuKey(String botName, String menuKey) {
        return botMenuRepository.findByBotNameAndMenuKey(botName, menuKey)
                .map(this::entityToKeyboard)
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No menu found in DB for bot={} menuKey={}, will use fallback", botName, menuKey);
                    return Mono.empty();
                }));
    }

    /**
     * 根据 botName + menuLevel 查找主菜单（level=1）的第一个菜单并转换
     */
    public Mono<InlineKeyboardMarkupDto> findMainMenuByBotName(String botName, int menuLevel) {
        return botMenuRepository.findByBotNameAndMenuLevel(botName, menuLevel)
                .next()
                .map(this::entityToKeyboard)
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No main menu found in DB for bot={} level={}, will use fallback", botName, menuLevel);
                    return Mono.empty();
                }));
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
