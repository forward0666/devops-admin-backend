package com.backend.user.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.user.entity.MiddlewareEntity;
import com.backend.user.entity.ProjectMemberEntity;
import com.backend.user.service.MiddlewareService;
import com.backend.user.service.ProjectMemberService;
import com.backend.utils.JwtUtil;
import com.backend.utils.exception.BizException;
import com.backend.user.vo.MiddlewareVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/middleware")
@RequiredArgsConstructor
public class MiddlewareController {

    private final MiddlewareService middlewareService;
    private final ProjectMemberService projectMemberService;
    private final JwtUtil jwtUtil;

    @GetMapping("/list")
    public ResponseEntity<ApiResponseDto<List<MiddlewareVo>>> list(@RequestParam Long projectId, HttpServletRequest request) {
        List<MiddlewareEntity> list = middlewareService.findByProjectId(projectId);
        String projectRole = resolveProjectRole(request, projectId);
        list = applyMiddlewareFilter(list, projectRole);
        return ResponseEntity.ok(ApiResponseDto.success("Success", list.stream().map(MiddlewareVo::fromEntity).toList()));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<MiddlewareVo>> create(@RequestBody Map<String, String> body) {
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
        return ResponseEntity.ok(ApiResponseDto.success("Middleware created", MiddlewareVo.fromEntity(created)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<MiddlewareVo>> update(@PathVariable String id, @RequestBody Map<String, String> body) {
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
        if (updated == null) throw new BizException(404, "Middleware not found");
        return ResponseEntity.ok(ApiResponseDto.success("Middleware updated", MiddlewareVo.fromEntity(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable String id, @RequestParam Long projectId) {
        boolean deleted = middlewareService.delete(id, projectId);
        if (!deleted) throw new BizException(404, "Middleware not found");
        return ResponseEntity.ok(ApiResponseDto.success("Middleware deleted", null));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponseDto<Integer>> importMiddlewares(@RequestBody Map<String, Object> body) {
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
        return ResponseEntity.ok(ApiResponseDto.success("Imported " + entities.size() + " middlewares", entities.size()));
    }

    @PostMapping("/bulkUpdate")
    public ResponseEntity<ApiResponseDto<Integer>> bulkUpdate(@RequestBody Map<String, Object> body) {
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
        return ResponseEntity.ok(ApiResponseDto.success("Updated " + count + " middlewares", count));
    }

    private String resolveProjectRole(HttpServletRequest request, Long projectId) {
        if (Boolean.TRUE.equals(request.getAttribute("internalCall"))) {
            return "Administrator";
        }
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

    private List<MiddlewareEntity> applyMiddlewareFilter(List<MiddlewareEntity> list, String role) {
        if ("None".equals(role)) return List.of();
        if ("Administrator".equals(role) || "DevOps".equals(role)) return list;
        return list.stream().filter(m -> !"prod".equals(m.getEnv())).toList();
    }
}