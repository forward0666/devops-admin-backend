package com.backend.bot.controller;

import com.backend.bot.entity.BotAuthorizedChatEntity;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.repository.BotAuthorizedChatRepository;
import com.backend.bot.service.BotCoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 授权聊天查询控制器
 * 
 * 提供API接口用于查询Bot的授权聊天列表和统计信息。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/chats/authorization/query")
public class ChatAuthorizationQueryController {

    private final BotCoreService botCoreService;
    private final BotAuthorizedChatRepository authorizedChatRepository;

    /**
     * 获取Bot的所有授权聊天
     * 
     * @param botName Bot名称
     * @return 授权聊天列表
     */
    @GetMapping("/bot/{botName}/chats")
    public Mono<ResponseEntity<Map<String, Object>>> getBotAuthorizedChats(@PathVariable String botName) {
        // 验证Bot是否存在
        return botCoreService.findByBotName(botName)
                .flatMap(bot -> {
                    // 查询所有授权聊天
                    return authorizedChatRepository.findAllByBotConfigId(bot.getId())
                            .collectList()
                            .map(chats -> {
                                Map<String, Object> data = new HashMap<>();
                                data.put("botName", botName);
                                data.put("botId", bot.getId());
                                data.put("authorizedChats", chats);
                                return HttpResponseUtils.ok(data);
                            });
                })
                .defaultIfEmpty(HttpResponseUtils.notFound("未找到指定名称的Bot"));
    }

    /**
     * 获取聊天授权的所有Bot
     * 
     * @param chatId 聊天ID
     * @return 授权的Bot列表
     */
    @GetMapping("/chat/{chatId}/bots")
    public Mono<ResponseEntity<Map<String, Object>>> getChatAuthorizedBots(@PathVariable Long chatId) {
        // 查询所有包含该chatId的授权
        return authorizedChatRepository.findAllByChatId(chatId)
                .flatMap(authorization -> {
                    // 根据botConfigId查询Bot信息
                    return botCoreService.findByBotId(authorization.getBotConfigId())
                            .map(bot -> {
                                Map<String, Object> botInfo = new HashMap<>();
                                botInfo.put("id", bot.getId());
                                botInfo.put("botName", bot.getBotName());
                                botInfo.put("botUsername", bot.getBotUsername());
                                botInfo.put("status", bot.getStatus());
                                botInfo.put("authorizationType", authorization.getType());
                                botInfo.put("authorizationStatus", authorization.getStatus());
                                return botInfo;
                            });
                })
                .collectList()
                .map(bots -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("chatId", chatId);
                    data.put("authorizedBots", bots);
                    return HttpResponseUtils.ok(data);
                });
    }

    /**
     * 删除特定授权
     * 
     * @param id 授权记录ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteAuthorization(@PathVariable Long id) {
        return authorizedChatRepository.findById(id)
                .flatMap(auth -> {
                    // 删除授权记录
                    return authorizedChatRepository.deleteById(id)
                            .then(Mono.just(auth));
                })
                .map(auth -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", id);
                    data.put("botConfigId", auth.getBotConfigId());
                    data.put("chatId", auth.getChatId());
                    return HttpResponseUtils.ok(data);
                })
                .defaultIfEmpty(HttpResponseUtils.notFound("未找到指定的授权记录"));
    }

    /**
     * 获取Bot授权统计信息
     * 
     * @param botName Bot名称
     * @return 统计信息
     */
    @GetMapping("/bot/{botName}/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getBotAuthorizationStats(@PathVariable String botName) {
        // 验证Bot是否存在
        return botCoreService.findByBotName(botName)
                .flatMap(bot -> {
                    // 查询所有授权聊天
                    return authorizedChatRepository.findAllByBotConfigId(bot.getId())
                            .collectList()
                            .map(chats -> {
                                // 计算统计信息
                                Map<String, Object> stats = new HashMap<>();
                                stats.put("botName", botName);
                                stats.put("botId", bot.getId());
                                stats.put("totalAuthorizations", chats.size());
                                
                                // 按状态分组统计
                                long activeCount = chats.stream()
                                        .filter(chat -> chat.getStatus() != null && chat.getStatus() == 1)
                                        .count();
                                stats.put("activeAuthorizations", activeCount);
                                stats.put("inactiveAuthorizations", chats.size() - activeCount);
                                
                                // 按类型分组统计
                                Map<String, Long> typeStats = new HashMap<>();
                                chats.forEach(chat -> {
                                    String type = chat.getType() != null ? chat.getType() : "unknown";
                                    typeStats.put(type, typeStats.getOrDefault(type, 0L) + 1);
                                });
                                stats.put("authorizationByType", typeStats);
                                
                                // 最近的授权时间
                                LocalDateTime latestAuth = chats.stream()
                                        .filter(chat -> chat.getCreatedAt() != null)
                                        .map(BotAuthorizedChatEntity::getCreatedAt)
                                        .max(LocalDateTime::compareTo)
                                        .orElse(null);
                                stats.put("latestAuthorization", latestAuth);
                                
                                return HttpResponseUtils.ok(stats);
                            });
                })
                .defaultIfEmpty(HttpResponseUtils.notFound("未找到指定名称的Bot"));
    }
}