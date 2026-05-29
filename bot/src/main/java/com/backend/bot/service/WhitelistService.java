package com.backend.bot.service;

import reactor.core.publisher.Mono;

/**
 * 域名加白业务逻辑服务接口。
 * 负责接收解析后的IP和用户信息，并执行实际的加白操作。
 */
public interface WhitelistService {

    Mono<Boolean> addIpToWhitelist(String ip, String username, String domainType);

    Mono<Boolean> removeIpFromWhitelist(String ip, String domainType);

    Mono<java.util.List<String>> getWhitelistIps(String domainType);

    Mono<String> addCfWhitelistIp(Long projectId, String ruleId, String ip, String username, String env);
}