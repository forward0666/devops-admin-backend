package com.backend.cloudflare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final CfClientService cfClientService;

    /**
     * Verify a Cloudflare API token.
     *
     * @param apiToken the API token to verify
     * @return Cloudflare API response
     */
    public Map<String, Object> verifyToken(String apiToken) {
        log.debug("Verifying Cloudflare API token");
        return cfClientService.verifyToken(apiToken);
    }

    /**
     * List all Cloudflare accounts accessible by the token.
     *
     * @param apiToken the API token
     * @return Cloudflare API response with accounts list
     */
    public Map<String, Object> listAccounts(String apiToken) {
        log.debug("Listing Cloudflare accounts");
        return cfClientService.listAccounts(apiToken);
    }
}