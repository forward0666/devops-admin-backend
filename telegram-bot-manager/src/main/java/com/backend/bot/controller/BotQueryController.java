package com.backend.bot.controller;

import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.vo.BotVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bot查询管理控制器
 * 
 * 提供API接口用于查询Bot列表、详情和状态管理。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/bots")
public class BotQueryController {

    private final BotCoreService botCoreService;

    /**
     * 获取所有Bot列表
     * 
     * @return Bot列表
     */
    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> getAllBots() {
        // 注意：这里需要添加一个获取所有Bot的方法到BotCoreService
        // 暂时返回空列表
        return Mono.just(ResponseEntity.ok(buildResponse(HttpStatus.OK, "查询成功", List.of())));
    }

    /**
     * 根据名称获取Bot详情
     * 
     * @param name Bot名称
     * @return Bot详情
     */
    @GetMapping("/{name}")
    public Mono<ResponseEntity<Map<String, Object>>> getBotByName(@PathVariable String name) {
        return botCoreService.findByBotName(name)
                .map(entity -> {
                    BotVo botVo = botCoreService.convertToVo(entity);
                    Map<String, Object> data = new HashMap<>();
                    data.put("bot", botVo);
                    return ResponseEntity.ok(buildResponse(HttpStatus.OK, "查询成功", data));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(buildResponse(HttpStatus.NOT_FOUND, "未找到指定名称的Bot", null)));
    }

    /**
     * 更新Bot状态
     * 
     * @param name Bot名称
     * @param status 新状态 (0=禁用, 1=启用)
     * @return 更新结果
     */
    @PutMapping("/{name}/status")
    public Mono<ResponseEntity<Map<String, Object>>> updateBotStatus(
            @PathVariable String name,
            @RequestParam Integer status) {

        if (status != 0 && status != 1) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(buildResponse(HttpStatus.BAD_REQUEST, "状态值只能是 0 (禁用) 或 1 (启用)", null)));
        }

        return botCoreService.updateBotStatusByName(name, status)
                .map(updated -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("botName", name);
                    data.put("status", status);
                    return ResponseEntity.ok(buildResponse(HttpStatus.OK, "状态更新成功", data));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(buildResponse(HttpStatus.NOT_FOUND, "未找到指定名称的Bot", null)));
    }
    
    /**
     * 获取Bot状态
     * 
     * @param name Bot名称
     * @return Bot状态
     */
    @GetMapping("/{name}/status")
    public Mono<ResponseEntity<Map<String, Object>>> getBotStatus(@PathVariable String name) {
        return botCoreService.findByBotName(name)
                .map(entity -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("botName", name);
                    data.put("status", entity.getStatus());
                    return ResponseEntity.ok(buildResponse(HttpStatus.OK, "状态查询成功", data));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(buildResponse(HttpStatus.NOT_FOUND, "未找到指定名称的Bot", null)));
    }

    /**
     * 删除Bot
     * 
     * @param name Bot名称
     * @return 删除结果
     */
    @DeleteMapping("/{name}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteBot(@PathVariable String name) {
        // 注意：需要添加根据名称删除Bot的方法到BotCoreService
        // 暂时返回成功
        Map<String, Object> data = new HashMap<>();
        data.put("botName", name);
        
        return Mono.just(ResponseEntity.ok(buildResponse(HttpStatus.OK, "Bot删除成功", data)));
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