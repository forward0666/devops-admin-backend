package com.backend.bot.service;

import reactor.core.publisher.Mono;

/**
 * 域名加白业务逻辑服务接口。
 * 负责接收解析后的IP和用户信息，并执行实际的加白操作。
 */
public interface WhitelistService {

    /**
     * 将指定的IP和用户名添加到特定类型的域名白名单中。
     *
     * @param ip 待加白的IP地址。
     * @param username 操作的用户名。
     * @param domainType 域名类型（如 "前台域名", "后台域名"）。
     * @return Mono<Boolean> - 表示操作是否成功的异步结果。
     */
    Mono<Boolean> addIpToWhitelist(String ip, String username, String domainType);
}