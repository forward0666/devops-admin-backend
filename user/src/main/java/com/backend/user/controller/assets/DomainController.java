package com.backend.user.controller.assets;

import com.backend.user.dto.ApiResponseDto;
import com.backend.user.entity.mongo.DomainEntity;
import com.backend.user.entity.system.ProjectMemberEntity;
import com.backend.user.service.mongo.DomainService;
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
@RequestMapping("/domain")
public class DomainController {

    @Autowired
    private DomainService domainService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/list")
    public ApiResponseDto<List<DomainEntity>> list(@RequestParam Long projectId, HttpServletRequest request) {
        try {
            List<DomainEntity> domains = domainService.findByProjectId(projectId);
            String projectRole = getProjectRole(request, projectId);

            // Member in prod: only show web type
            if ("Member".equals(projectRole)) {
                domains = domains.stream()
                    .filter(d -> !"prod".equals(d.getEnv()) || "web".equals(d.getType()))
                    .toList();
            }

            return ApiResponseDto.success("Success", domains);
        } catch (Exception e) {
            log.error("Failed to fetch domains", e);
            return ApiResponseDto.error("Failed to fetch domains");
        }
    }

    @PostMapping
    public ApiResponseDto<DomainEntity> create(@RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            Long projectId = Long.parseLong(body.get("projectId"));
            DomainEntity entity = new DomainEntity();
            entity.setDomain(body.get("domain"));
            entity.setEnv(body.get("env"));
            entity.setType(body.get("type"));
            entity.setRemark(body.get("remark"));
            DomainEntity created = domainService.create(projectId, entity);
            return ApiResponseDto.success("Domain created", created);
        } catch (Exception e) {
            log.error("Failed to create domain", e);
            return ApiResponseDto.error("Failed to create domain");
        }
    }

    @PutMapping("/{id}")
    public ApiResponseDto<DomainEntity> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            Long projectId = Long.parseLong(body.get("projectId"));
            DomainEntity entity = new DomainEntity();
            entity.setDomain(body.get("domain"));
            entity.setType(body.get("type"));
            entity.setRemark(body.get("remark"));
            DomainEntity updated = domainService.update(id, projectId, entity);
            if (updated == null) return ApiResponseDto.error("Domain not found");
            return ApiResponseDto.success("Domain updated", updated);
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
