package com.backend.bot.controller;

import com.backend.bot.service.BotCoreService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Bot授权聊天管理控制器
 * 
 * 提供API接口用于管理Bot的授权聊天列表，包括添加、删除和更新授权状态。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/chats/authorization")
public class ChatAuthorizationController {

    private final BotCoreService botCoreService;

    /**
     * 添加授权聊天
     * 
     * @param botName Bot名称
     * @param botConfigId Bot配置ID
     * @param chatId 聊天ID
     * @param chatName 聊天名称（可选）
     * @param type 聊天类型（private, group, supergroup等）
     * @return 添加结果
     */
    @PostMapping("/add")
    public Mono<ResponseEntity<Map<String, Object>>> addAuthorizedChat(
            @RequestParam @NotNull String botName,
            @RequestParam @NotNull Long botConfigId,
            @RequestParam @NotNull Long chatId,
            @RequestParam(required = false) String chatName,
            @RequestParam(required = false, defaultValue = "private") String type) {

        return botCoreService.addAuthorizedChat(botName, botConfigId, chatId, chatName, type)
                .map(success -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("botName", botName);
                    response.put("chatId", chatId);
                    
                    if (Boolean.TRUE.equals(success)) {
                        return ResponseEntity.ok(buildResponse(HttpStatus.OK, "✅ 聊天授权添加成功", response));
                    } else {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(buildResponse(HttpStatus.CONFLICT, "⚠️ 聊天已存在于授权列表中", response));
                    }
                })
                .onErrorResume(e -> {
                    log.error("❌ 添加授权聊天失败", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "❌ 添加授权聊天失败: " + e.getMessage(), null)));
                });
    }

    /**
     * 更新授权聊天状态
     * 
     * @param botName Bot名称
     * @param botConfigId Bot配置ID
     * @param chatId 聊天ID
     * @param status 状态 (0=禁用, 1=启用)
     * @return 更新结果
     */
    @PostMapping("/updateStatus")
    public Mono<ResponseEntity<Map<String, Object>>> updateAuthorizationStatus(
            @RequestParam @NotNull String botName,
            @RequestParam @NotNull Long botConfigId,
            @RequestParam @NotNull Long chatId,
            @RequestParam @NotNull Integer status) {

        if (status != 0 && status != 1) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(buildResponse(HttpStatus.BAD_REQUEST, "状态值只能是 0 (禁用) 或 1 (启用)", null)));
        }

        return botCoreService.updateChatAuthorizationStatus(botName, botConfigId, chatId, status)
                .map(success -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("botName", botName);
                    response.put("chatId", chatId);
                    response.put("status", status);
                    
                    if (Boolean.TRUE.equals(success)) {
                        return ResponseEntity.ok(buildResponse(HttpStatus.OK, "✅ 授权状态更新成功", response));
                    } else {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(buildResponse(HttpStatus.NOT_FOUND, "⚠️ 未找到指定的授权聊天记录", response));
                    }
                })
                .onErrorResume(e -> {
                    log.error("❌ 更新授权状态失败", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "❌ 更新授权状态失败: " + e.getMessage(), null)));
                });
    }

    /**
     * 构建统一响应结构
     */
    private Map<String, Object> buildResponse(HttpStatus status, String msg, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", status.is2xxSuccessful() ? "ok" : "error");
        result.put("code", status.value());
        result.put("message", msg);
        if (data != null) {
            result.put("data", data);
        }
        return result;
    }
}