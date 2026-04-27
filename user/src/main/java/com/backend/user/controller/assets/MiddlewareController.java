package com.backend.user.controller.assets;

import com.backend.user.dto.ApiResponseDto;
import com.backend.user.entity.mongo.MiddlewareEntity;
import com.backend.user.entity.system.ProjectMemberEntity;
import com.backend.user.service.mongo.MiddlewareService;
import com.backend.user.service.system.ProjectMemberService;
import com.backend.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/middleware")
public class MiddlewareController {

    @Autowired
    private MiddlewareService middlewareService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/list")
    public ApiResponseDto<List<MiddlewareEntity>> list(@RequestParam Long projectId, HttpServletRequest request) {
        try {
            List<MiddlewareEntity> list = middlewareService.findByProjectId(projectId);
            String projectRole = getProjectRole(request, projectId);

            // Member: hide prod environment middlewares
            if ("Member".equals(projectRole)) {
                list = list.stream().filter(m -> !"prod".equals(m.getEnv())).toList();
            }

            return ApiResponseDto.success("Success", list);
        } catch (Exception e) {
            log.error("Failed to fetch middlewares", e);
            return ApiResponseDto.error("Failed to fetch middlewares");
        }
    }

    @PostMapping
    public ApiResponseDto<MiddlewareEntity> create(@RequestBody Map<String, String> body) {
        try {
            Long projectId = Long.parseLong(body.get("projectId"));
            MiddlewareEntity entity = new MiddlewareEntity();
            entity.setName(body.get("name"));
            entity.setEnv(body.get("env"));
            entity.setProtocol(body.get("protocol"));
            entity.setExternalAddr(body.get("externalAddr"));
            entity.setInternalAddr(body.get("internalAddr"));
            entity.setSvcAddr(body.get("svcAddr"));
            entity.setRemark(body.get("remark"));
            MiddlewareEntity created = middlewareService.create(projectId, entity);
            return ApiResponseDto.success("Middleware created", created);
        } catch (Exception e) {
            log.error("Failed to create middleware", e);
            return ApiResponseDto.error("Failed to create middleware");
        }
    }

    @PutMapping("/{id}")
    public ApiResponseDto<MiddlewareEntity> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            Long projectId = Long.parseLong(body.get("projectId"));
            MiddlewareEntity entity = new MiddlewareEntity();
            entity.setName(body.get("name"));
            entity.setProtocol(body.get("protocol"));
            entity.setExternalAddr(body.get("externalAddr"));
            entity.setInternalAddr(body.get("internalAddr"));
            entity.setSvcAddr(body.get("svcAddr"));
            entity.setRemark(body.get("remark"));
            MiddlewareEntity updated = middlewareService.update(id, projectId, entity);
            if (updated == null) return ApiResponseDto.error("Middleware not found");
            return ApiResponseDto.success("Middleware updated", updated);
        } catch (Exception e) {
            log.error("Failed to update middleware", e);
            return ApiResponseDto.error("Failed to update middleware");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponseDto<Void> delete(@PathVariable String id, @RequestParam Long projectId) {
        try {
            boolean deleted = middlewareService.delete(id, projectId);
            if (!deleted) return ApiResponseDto.error("Middleware not found");
            return ApiResponseDto.success("Middleware deleted", null);
        } catch (Exception e) {
            log.error("Failed to delete middleware", e);
            return ApiResponseDto.error("Failed to delete middleware");
        }
    }

    @PostMapping("/import")
    public ApiResponseDto<Integer> importMiddlewares(@RequestBody Map<String, Object> body) {
        try {
            Long projectId = Long.parseLong(body.get("projectId").toString());
            @SuppressWarnings("unchecked")
            List<Map<String, String>> items = (List<Map<String, String>>) body.get("middlewares");
            List<MiddlewareEntity> entities = items.stream().map(m -> {
                MiddlewareEntity e = new MiddlewareEntity();
                e.setName(m.get("name"));
                e.setEnv(m.get("env"));
                e.setProtocol(m.getOrDefault("protocol", ""));
                e.setExternalAddr(m.getOrDefault("externalAddr", ""));
                e.setInternalAddr(m.getOrDefault("internalAddr", ""));
                e.setSvcAddr(m.getOrDefault("svcAddr", ""));
                e.setRemark(m.getOrDefault("remark", ""));
                return e;
            }).toList();
            middlewareService.importMiddlewares(projectId, entities);
            return ApiResponseDto.success("Imported " + entities.size() + " middlewares", entities.size());
        } catch (Exception e) {
            log.error("Failed to import middlewares", e);
            return ApiResponseDto.error("Failed to import middlewares");
        }
    }

    private String getProjectRole(HttpServletRequest request, Long projectId) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) return "Member";
            ProjectMemberEntity member = projectMemberService.findByProjectIdAndUserId(projectId, userId);
            return member != null ? member.getProjectRole() : "Member";
        } catch (Exception e) {
            return "Member";
        }
    }
}
