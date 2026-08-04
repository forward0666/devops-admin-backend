package com.backend.cloudflare.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.cloudflare.entity.AccountEntity;
import com.backend.cloudflare.mapper.AccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 账户管理 Controller
 * 对应 Python 的 routes/accounts.py
 */
@Slf4j
@RestController
@RequestMapping("")
public class AccountController {

    @Autowired
    private AccountMapper accountMapper;

    @GetMapping("/account")
    public ResponseEntity<ApiResponseDto<List<AccountEntity>>> listAccounts(
            @RequestParam(defaultValue = "false") boolean raw) {
        List<AccountEntity> accounts = accountMapper.findAll();
        if (!raw) {
            for (AccountEntity acc : accounts) {
                if (acc.getApiKey() != null && acc.getApiKey().length() > 8) {
                    String key = acc.getApiKey();
                    acc.setApiKey(key.substring(0, 4) + "••••••••" + key.substring(key.length() - 4));
                }
            }
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", accounts));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponseDto<AccountEntity>> getAccount(@PathVariable Long accountId) {
        AccountEntity acc = accountMapper.findById(accountId);
        if (acc == null) {
            return ResponseEntity.status(404).body(ApiResponseDto.error("Account not found"));
        }
        if (acc.getApiKey() != null && acc.getApiKey().length() > 8) {
            String key = acc.getApiKey();
            acc.setApiKey(key.substring(0, 4) + "••••••••" + key.substring(key.length() - 4));
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", acc));
    }

    @PostMapping("/account")
    public ResponseEntity<ApiResponseDto<String>> createAccount(@RequestBody AccountEntity body) {
        String name = body.getName() != null ? body.getName().trim() : "";
        String apiKey = body.getApiKey() != null ? body.getApiKey().trim() : "";
        if (name.isEmpty() || apiKey.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponseDto.error("name and apiKey are required"));
        }
        body.setName(name);
        body.setApiKey(apiKey);
        accountMapper.insert(body);
        return ResponseEntity.ok(ApiResponseDto.success("ok", "ok"));
    }

    @PutMapping("/account/{accountId}")
    public ResponseEntity<ApiResponseDto<String>> updateAccount(@PathVariable Long accountId, @RequestBody AccountEntity body) {
        AccountEntity existing = accountMapper.findById(accountId);
        if (existing == null) {
            return ResponseEntity.status(404).body(ApiResponseDto.error("Account not found"));
        }
        String name = body.getName() != null ? body.getName().trim() : "";
        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponseDto.error("name is required"));
        }
        body.setId(accountId);
        accountMapper.updateById(body);
        return ResponseEntity.ok(ApiResponseDto.success("ok", "ok"));
    }

    @DeleteMapping("/account/{accountId}")
    public ResponseEntity<ApiResponseDto<String>> deleteAccount(@PathVariable Long accountId) {
        AccountEntity existing = accountMapper.findById(accountId);
        if (existing == null) {
            return ResponseEntity.status(404).body(ApiResponseDto.error("Account not found"));
        }
        accountMapper.deleteById(accountId);
        return ResponseEntity.ok(ApiResponseDto.success("ok", "ok"));
    }

    @GetMapping("/account/{accountId}/key")
    public ResponseEntity<ApiResponseDto<Object>> getAccountKey(@PathVariable Long accountId) {
        String apiKey = accountMapper.findApiKeyById(accountId);
        if (apiKey == null) {
            return ResponseEntity.status(404).body(ApiResponseDto.error("Account not found"));
        }
        String masked = apiKey.length() > 8
                ? apiKey.substring(0, 4) + "••••••••" + apiKey.substring(apiKey.length() - 4)
                : "••••••••";
        return ResponseEntity.ok(ApiResponseDto.success("ok", Map.of("maskedKey", masked)));
    }

    @GetMapping("/account/{accountId}/token")
    public ResponseEntity<ApiResponseDto<Object>> getAccountToken(@PathVariable Long accountId) {
        String apiKey = accountMapper.findApiKeyById(accountId);
        if (apiKey == null) {
            return ResponseEntity.status(404).body(ApiResponseDto.error("Account not found"));
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", Map.of("token", apiKey)));
    }
}