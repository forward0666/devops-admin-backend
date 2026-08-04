package com.backend.agent.controller;

import com.backend.agent.entity.ModelEntity;
import com.backend.agent.service.ModelService;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.utils.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Model CRUD endpoints.
 * Matches agent-bak/app/routes/model.py surface.
 */
@Slf4j
@RestController
@RequestMapping("/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<ApiResponseDto<?>> list() {
        try {
            var models = modelService.list();
            // Mask API key for security
            models.forEach(m -> {
                if (m.getApiKey() != null && m.getApiKey().length() > 12) {
                    String key = m.getApiKey();
                    m.setApiKey(key.substring(0, 8) + "***" + key.substring(key.length() - 4));
                }
            });
            return ResponseEntity.ok(ApiResponseDto.success("ok", models));
        } catch (Exception e) {
            log.error("list models error", e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> get(@PathVariable Integer id) {
        try {
            var model = modelService.getById(id);
            if (model == null) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "Model not found"));
            }
            return ResponseEntity.ok(ApiResponseDto.success("ok", model));
        } catch (Exception e) {
            log.error("get model {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<?>> create(@RequestBody Map<String, Object> body) {
        try {
            String name = (body.get("name") != null ? ((String) body.get("name")).trim() : "");
            String provider = (body.get("provider") != null ? ((String) body.get("provider")).trim() : "");
            String model = (body.get("model") != null ? ((String) body.get("model")).trim() : "");
            if (name.isEmpty()) {
                throw new BizException(400, "Name is required");
            }
            if (provider.isEmpty()) {
                throw new BizException(400, "Provider is required");
            }
            if (model.isEmpty()) {
                throw new BizException(400, "Model ID is required");
            }
            ModelEntity entity = objectMapper.convertValue(body, ModelEntity.class);
            modelService.create(entity);
            return ResponseEntity.ok(ApiResponseDto.success("Model created", entity));
        } catch (BizException e) {
            return ResponseEntity.ok(ApiResponseDto.error(e.getHttpStatus(), e.getMessage()));
        } catch (Exception e) {
            log.error("create model error", e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> update(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        try {
            var existing = modelService.getById(id);
            if (existing == null) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "Model not found"));
            }
            ModelEntity entity = objectMapper.convertValue(body, ModelEntity.class);
            var updated = modelService.update(id, entity);
            return ResponseEntity.ok(ApiResponseDto.success("Model updated", updated));
        } catch (Exception e) {
            log.error("update model {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> delete(@PathVariable Integer id) {
        try {
            boolean deleted = modelService.delete(id);
            if (!deleted) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "Model not found"));
            }
            return ResponseEntity.ok(ApiResponseDto.success("Model deleted", null));
        } catch (Exception e) {
            log.error("delete model {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> patch(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }
}