package com.backend.user.controller;

import com.backend.user.dto.ApiResponseDto;
import com.backend.user.entity.DomainEntity;
import com.backend.user.entity.ProjectMemberEntity;
import com.backend.user.service.DomainService;
import com.backend.user.service.ProjectMemberService;
import com.backend.user.util.JwtUtil;
import com.backend.user.vo.DomainVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/domain")
public class DomainController {

    @Autowired
    private DomainService domainService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/list")
    public ApiResponseDto<List<DomainVo>> list(@RequestParam Long projectId, HttpServletRequest request) {
        try {
            List<DomainEntity> domains = domainService.findByProjectId(projectId);
            String projectRole = resolveProjectRole(request, projectId);

            domains = applyDomainFilter(domains, projectRole);

            return ApiResponseDto.success("Success", domains.stream().map(DomainVo::fromEntity).toList());
        } catch (Exception e) {
            log.error("Failed to fetch domains", e);
            return ApiResponseDto.error("Failed to fetch domains");
        }
    }

    @PostMapping
    public ApiResponseDto<DomainVo> create(@RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            Long projectId = Long.parseLong(body.get("projectId"));
            DomainEntity entity = new DomainEntity();
            entity.setDomain(body.get("domain"));
            entity.setEnv(body.get("env"));
            entity.setType(body.get("type"));
            entity.setRemark(body.get("remark"));
            DomainEntity created = domainService.create(projectId, entity);
            return ApiResponseDto.success("Domain created", DomainVo.fromEntity(created));
        } catch (Exception e) {
            log.error("Failed to create domain", e);
            return ApiResponseDto.error("Failed to create domain");
        }
    }

    @PutMapping("/{id}")
    public ApiResponseDto<DomainVo> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            Long projectId = Long.parseLong(body.get("projectId"));
            DomainEntity entity = new DomainEntity();
            entity.setDomain(body.get("domain"));
            entity.setType(body.get("type"));
            entity.setRemark(body.get("remark"));
            DomainEntity updated = domainService.update(id, projectId, entity);
            if (updated == null) return ApiResponseDto.error("Domain not found");
            return ApiResponseDto.success("Domain updated", DomainVo.fromEntity(updated));
        } catch (Exception e) {
            log.error("Failed to update domain", e);
            return ApiResponseDto.error("Failed to update domain");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponseDto<Void> delete(@PathVariable String id, @RequestParam Long projectId) {
        try {
            boolean deleted = domainService.delete(id, projectId);
            if (!deleted) return ApiResponseDto.error("Domain not found");
            return ApiResponseDto.success("Domain deleted", null);
        } catch (Exception e) {
            log.error("Failed to delete domain", e);
            return ApiResponseDto.error("Failed to delete domain");
        }
    }

    @PostMapping("/import")
    public ApiResponseDto<Integer> importDomains(@RequestBody Map<String, Object> body) {
        try {
            Long projectId = Long.parseLong(body.get("projectId").toString());
            @SuppressWarnings("unchecked")
            List<Map<String, String>> items = (List<Map<String, String>>) body.get("domains");
            List<DomainEntity> domains = items.stream().map(m -> {
                DomainEntity d = new DomainEntity();
                d.setDomain(m.get("domain"));
                d.setEnv(m.get("env"));
                d.setType(m.getOrDefault("type", "web"));
                d.setRemark(m.getOrDefault("remark", ""));
                return d;
            }).toList();
            domainService.importDomains(projectId, domains);
            return ApiResponseDto.success("Imported " + domains.size() + " domains", domains.size());
        } catch (Exception e) {
            log.error("Failed to import domains", e);
            return ApiResponseDto.error("Failed to import domains");
        }
    }

    /**
     * 统一角色解析：优先 X-Tg-Username，其次 JWT
     */
    private String resolveProjectRole(HttpServletRequest request, Long projectId) {
        // bot 内部请求，通过 tgUsername 查角色
        String tgUsername = request.getHeader("X-Tg-Username");
        if (tgUsername != null && !tgUsername.isBlank()) {
            ProjectMemberEntity member = projectMemberService.findByProjectIdAndTgUsername(projectId, tgUsername);
            return member != null ? member.getProjectRole() : "None";
        }
        // 前端 JWT 请求
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
     * 按角色过滤域名
     * Administrator/DevOps/Leader: 全部可见
     * Member: prod 环境只显示 web 类型
     * None: 无权限，返回空
     */
    private List<DomainEntity> applyDomainFilter(List<DomainEntity> domains, String role) {
        if ("None".equals(role)) return List.of();
        if (!"Member".equals(role)) return domains;
        return domains.stream()
                .filter(d -> !"prod".equals(d.getEnv()) || "web".equals(d.getType()))
                .toList();
    }
}
