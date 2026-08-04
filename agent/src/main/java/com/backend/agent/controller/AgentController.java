package com.backend.agent.controller;

import com.backend.agent.dto.AgentChatRequest;
import com.backend.agent.entity.AgentEntity;
import com.backend.agent.service.AgentService;
import com.backend.agent.service.MCPService;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.utils.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent CRUD + discover + tools + chat endpoints.
 * Matches agent-bak/app/routes/agent.py surface.
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final MCPService mcpService;
    private final ObjectMapper objectMapper;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<ApiResponseDto<?>> list() {
        try {
            return ResponseEntity.ok(ApiResponseDto.success("ok", agentService.list()));
        } catch (Exception e) {
            log.error("list agents error", e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> get(@PathVariable Integer id) {
        try {
            var agent = agentService.getById(id);
            if (agent == null) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "Agent not found"));
            }
            return ResponseEntity.ok(ApiResponseDto.success("ok", agent));
        } catch (Exception e) {
            log.error("get agent {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<?>> create(@RequestBody Map<String, Object> body) {
        try {
            String name = (body.get("name") != null ? ((String) body.get("name")).trim() : "");
            String type = (body.get("type") != null ? ((String) body.get("type")).trim() : "");
            if (name.isEmpty()) {
                throw new BizException(400, "Name is required");
            }
            if (type.isEmpty()) {
                throw new BizException(400, "Type is required");
            }
            AgentEntity entity = objectMapper.convertValue(body, AgentEntity.class);
            agentService.create(entity);
            return ResponseEntity.ok(ApiResponseDto.success("Agent created", entity));
        } catch (BizException e) {
            return ResponseEntity.ok(ApiResponseDto.error(e.getHttpStatus(), e.getMessage()));
        } catch (Exception e) {
            log.error("create agent error", e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> update(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        try {
            var existing = agentService.getById(id);
            if (existing == null) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "Agent not found"));
            }
            AgentEntity entity = objectMapper.convertValue(body, AgentEntity.class);
            var updated = agentService.update(id, entity);
            return ResponseEntity.ok(ApiResponseDto.success("Agent updated", updated));
        } catch (Exception e) {
            log.error("update agent {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> delete(@PathVariable Integer id) {
        try {
            boolean deleted = agentService.delete(id);
            if (!deleted) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "Agent not found"));
            }
            return ResponseEntity.ok(ApiResponseDto.success("Agent deleted", null));
        } catch (Exception e) {
            log.error("delete agent {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> patch(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    // ==================== Search ====================

    @GetMapping("/search")
    public ResponseEntity<ApiResponseDto<?>> search(@RequestParam String keyword) {
        try {
            return ResponseEntity.ok(ApiResponseDto.success("ok", agentService.search(keyword)));
        } catch (Exception e) {
            log.error("search agents error", e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    // ==================== Status ====================

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponseDto<?>> checkStatus(@PathVariable Integer id) {
        try {
            var agent = agentService.getById(id);
            if (agent == null) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "Agent not found"));
            }
            String status = mcpService.checkWorkerStatus(agent.getType());
            agentService.updateAgentStatus(id, status);
            return ResponseEntity.ok(ApiResponseDto.success("ok", Map.of("status", status)));
        } catch (Exception e) {
            log.error("check agent {} status error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    // ==================== Discover MCP Services ====================

    @GetMapping("/discover")
    public ResponseEntity<ApiResponseDto<?>> discover() {
        try {
            var services = mcpService.discoverServices();
            return ResponseEntity.ok(ApiResponseDto.success("ok", services));
        } catch (Exception e) {
            log.error("discover services error", e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    // ==================== Chat ====================

    @PostMapping("/{id}/chat")
    public ResponseEntity<ApiResponseDto<?>> chat(@PathVariable Integer id, @RequestBody AgentChatRequest request) {
        try {
            String message = request.getMessage();
            String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
            if (message == null || message.isBlank()) {
                return ResponseEntity.ok(ApiResponseDto.error(400, "Message is required"));
            }
            var result = agentService.chat(id, message, sessionId);
            return ResponseEntity.ok(ApiResponseDto.success("ok", result));
        } catch (BizException e) {
            return ResponseEntity.ok(ApiResponseDto.error(e.getHttpStatus(), e.getMessage()));
        } catch (Exception e) {
            log.error("chat agent {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    // ==================== Chat History & Sessions ====================

    @GetMapping("/{id}/chat/history")
    public ResponseEntity<ApiResponseDto<?>> chatHistory(@PathVariable Integer id,
                                                          @RequestParam(defaultValue = "default") String sessionId) {
        try {
            var result = agentService.getChatHistory(id, sessionId);
            return ResponseEntity.ok(ApiResponseDto.success("ok", result));
        } catch (Exception e) {
            log.error("get chat history for agent {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @GetMapping("/{id}/chat/sessions")
    public ResponseEntity<ApiResponseDto<?>> chatSessions(@PathVariable Integer id) {
        try {
            var sessions = agentService.getChatSessions(id);
            return ResponseEntity.ok(ApiResponseDto.success("ok", sessions));
        } catch (Exception e) {
            log.error("get chat sessions for agent {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/chat/sessions/{sessionId}")
    public ResponseEntity<ApiResponseDto<?>> deleteChatSession(@PathVariable Integer id,
                                                                @PathVariable String sessionId) {
        try {
            agentService.deleteChatSession(id, sessionId);
            return ResponseEntity.ok(ApiResponseDto.success("Session deleted", null));
        } catch (Exception e) {
            log.error("delete chat session {} for agent {} error", sessionId, id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    // ==================== Agent Tools / Files ====================

    @GetMapping("/{id}/tools")
    public ResponseEntity<ApiResponseDto<?>> getAgentTools(@PathVariable Integer id) {
        try {
            var tools = agentService.getAgentTools(id);
            return ResponseEntity.ok(ApiResponseDto.success("ok", tools));
        } catch (Exception e) {
            log.error("get agent {} tools error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PutMapping("/{id}/tools")
    public ResponseEntity<ApiResponseDto<?>> updateAgentTools(@PathVariable Integer id,
                                                               @RequestBody Map<String, Object> body) {
        try {
            agentService.updateAgentTools(id, body);
            return ResponseEntity.ok(ApiResponseDto.success("Updated", null));
        } catch (Exception e) {
            log.error("update agent {} tools error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }
}