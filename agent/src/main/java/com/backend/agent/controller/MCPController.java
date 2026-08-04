package com.backend.agent.controller;

import com.backend.agent.service.MCPService;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.utils.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP (Model Context Protocol) endpoints.
 * Matches agent-bak/app/routes/mcp.py surface.
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class MCPController {

    private final MCPService mcpService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<ApiResponseDto<?>> list() {
        try {
            return ResponseEntity.ok(ApiResponseDto.success("ok", mcpService.discoverServices()));
        } catch (Exception e) {
            log.error("list mcps error", e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<?>> create(@RequestBody Map<String, Object> body) {
        try {
            String name = (body.get("name") != null ? ((String) body.get("name")).trim() : "");
            if (name.isEmpty()) {
                throw new BizException(400, "Name is required");
            }
            Map<String, Object> result = mcpService.createMCP(body);
            return ResponseEntity.ok(ApiResponseDto.success("MCP created", result));
        } catch (BizException e) {
            return ResponseEntity.ok(ApiResponseDto.error(e.getHttpStatus(), e.getMessage()));
        } catch (Exception e) {
            log.error("create mcp error", e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> update(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        try {
            var result = mcpService.updateMCP(id, body);
            if (result == null) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "MCP not found"));
            }
            return ResponseEntity.ok(ApiResponseDto.success("MCP updated", result));
        } catch (Exception e) {
            log.error("update mcp {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> delete(@PathVariable Integer id) {
        try {
            boolean deleted = mcpService.deleteMCP(id);
            if (!deleted) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "MCP not found"));
            }
            return ResponseEntity.ok(ApiResponseDto.success("MCP deleted", null));
        } catch (Exception e) {
            log.error("delete mcp {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> patch(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }
}