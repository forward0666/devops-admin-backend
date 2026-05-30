package com.backend.user.controller;

import com.backend.user.dto.ApiResponseDto;
import com.backend.user.entity.MiddlewareEntity;
import com.backend.user.entity.ProjectMemberEntity;
import com.backend.user.service.MiddlewareService;
import com.backend.user.service.ProjectMemberService;
import com.backend.utils.JwtUtil;
import com.backend.user.vo.MiddlewareVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
    public ApiResponseDto<List<MiddlewareVo>> list(@RequestParam Long projectId, HttpServletRequest request) {
        try {
            List<MiddlewareEntity> list = middlewareService.findByProjectId(projectId);
            String projectRole = resolveProjectRole(request, projectId);

            list = applyMiddlewareFilter(list, projectRole);

            return ApiResponseDto.success("Success", list.stream().map(MiddlewareVo::fromEntity).toList());
        } catch (Exception e) {
            log.error("Failed to fetch middlewares", e);
            return ApiResponseDto.error("Failed to fetch middlewares");
        }
    }

    @PostMapping
    public ApiResponseDto<MiddlewareVo> create(@RequestBody Map<String, String> body) {
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
            entity.setType(body.get("type"));
            MiddlewareEntity created = middlewareService.create(projectId, entity);
            return ApiResponseDto.success("Middleware created", MiddlewareVo.fromEntity(created));
        } catch (Exception e) {
            log.error("Failed to create middleware", e);
            return ApiResponseDto.error("Failed to create middleware");
        }
    }

    @PutMapping("/{id}")
    public ApiResponseDto<MiddlewareVo> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            Long projectId = Long.parseLong(body.get("projectId"));
            MiddlewareEntity entity = new MiddlewareEntity();
            entity.setName(body.get("name"));
            entity.setProtocol(body.get("protocol"));
            entity.setExternalAddr(body.get("externalAddr"));
            entity.setInternalAddr(body.get("internalAddr"));
            entity.setSvcAddr(body.get("svcAddr"));
            entity.setRemark(body.get("remark"));
            entity.setType(body.get("type"));
            MiddlewareEntity updated = middlewareService.update(id, projectId, entity);
            if (updated == null) return ApiResponseDto.error("Middleware not found");
            return ApiResponseDto.success("Middleware updated", MiddlewareVo.fromEntity(updated));
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
                e.setType(m.getOrDefault("type", ""));
                return e;
            }).toList();
            middlewareService.importMiddlewares(projectId, entities);
            return ApiResponseDto.success("Imported " + entities.size() + " middlewares", entities.size());
        } catch (Exception e) {
            log.error("Failed to import middlewares", e);
            return ApiResponseDto.error("Failed to import middlewares");
        }
    }

    /**
     * 批量编辑中间件
     */
    @PostMapping("/bulkUpdate")
    public ApiResponseDto<Integer> bulkUpdate(@RequestBody Map<String, Object> body) {
        try {
            Long projectId = Long.parseLong(body.get("projectId").toString());
            @SuppressWarnings("unchecked")
            List<String> ids = ((List<?>) body.get("ids")).stream().map(Object::toString).toList();
            Map<String, Object> fields = new LinkedHashMap<>();
            if (body.containsKey("type") && body.get("type") != null && !body.get("type").toString().isBlank())
                fields.put("type", body.get("type").toString());
            if (body.containsKey("protocol") && body.get("protocol") != null && !body.get("protocol").toString().isBlank())
                fields.put("protocol", body.get("protocol").toString());
            if (body.containsKey("remark") && body.get("remark") != null && !body.get("remark").toString().isBlank())
                fields.put("remark", body.get("remark").toString());
            int count = middlewareService.bulkUpdate(projectId, ids, fields);
            return ApiResponseDto.success("Updated " + count + " middlewares", count);
        } catch (Exception e) {
            log.error("Failed to bulk update middlewares", e);
            return ApiResponseDto.error("Failed to bulk update middlewares");
        }
    }

    /**
     * 统一角色解析：优先 X-Tg-Username，其次 JWT
     */
    private String resolveProjectRole(HttpServletRequest request, Long projectId) {
        String tgUsername = request.getHeader("X-Tg-Username");
        if (tgUsername != null && !tgUsername.isBlank()) {
            ProjectMemberEntity member = projectMemberService.findByProjectIdAndTgUsername(projectId, tgUsername);
            return member != null ? member.getProjectRole() : "None";
        }
        return getProjectRoleFromJwt(request, projectId);
    }

    private String getProjectRoleFromJwt(HttpServletRequest request, Long projectId) {
        try {
            String token = request.getHeader("Authorization").substring(7);
            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) return "None";
            ProjectMemberEntity member = projectMemberService.findByProjectIdAndUserId(projectId, userId);
            return member != null ? member.getProjectRole() : "None";
        } catch (Exception e) {
            return "None";
        }
    }

    /**
     * 按角色过滤中间件
     * Administrator/DevOps: 全部可见
     * Leader/Member: 隐藏 prod 环境
     * None: 无权限，返回空
     */
    private List<MiddlewareEntity> applyMiddlewareFilter(List<MiddlewareEntity> list, String role) {
        if ("None".equals(role)) return List.of();
        if ("Administrator".equals(role) || "DevOps".equals(role)) return list;
        return list.stream().filter(m -> !"prod".equals(m.getEnv())).toList();
    }
}
