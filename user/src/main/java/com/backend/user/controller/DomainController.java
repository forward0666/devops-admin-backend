package com.backend.user.controller;

import java.util.Set;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.user.entity.DomainEntity;
import com.backend.user.entity.ProjectMemberEntity;
import com.backend.user.service.DomainService;
import com.backend.user.service.ProjectMemberService;
import com.backend.utils.JwtUtil;
import com.backend.utils.exception.BizException;
import com.backend.user.vo.DomainVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/domain")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;
    private final ProjectMemberService projectMemberService;
    private final JwtUtil jwtUtil;

    @GetMapping("/list")
    public ResponseEntity<ApiResponseDto<List<DomainVo>>> list(@RequestParam Long projectId, @RequestParam(required = false) String env, HttpServletRequest request) {
        List<DomainEntity> domains = domainService.findByProjectId(projectId);
        String projectRole = resolveProjectRole(request, projectId);
        domains = applyDomainFilter(domains, projectRole);
        if (env != null && !env.isBlank()) {
            domains = domains.stream().filter(d -> env.equalsIgnoreCase(d.getEnv())).toList();
        }
        return ResponseEntity.ok(ApiResponseDto.success("Success", domains.stream().map(DomainVo::fromEntity).toList()));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<DomainVo>> create(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long projectId = Long.parseLong(body.get("projectId"));
        DomainEntity entity = new DomainEntity();
        entity.setDomain(body.get("domain"));
        entity.setEnv(body.get("env"));
        entity.setType(body.get("type"));
        entity.setRemark(body.get("remark"));
        entity.setCdn(body.get("cdn"));
        DomainEntity created = domainService.create(projectId, entity);
        return ResponseEntity.ok(ApiResponseDto.success("Domain created", DomainVo.fromEntity(created)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<DomainVo>> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        Long projectId = Long.parseLong(body.get("projectId"));
        DomainEntity entity = new DomainEntity();
        entity.setDomain(body.get("domain"));
        entity.setType(body.get("type"));
        entity.setRemark(body.get("remark"));
        entity.setCdn(body.get("cdn"));
        DomainEntity updated = domainService.update(id, projectId, entity);
        if (updated == null) throw new BizException(404, "Domain not found");
        return ResponseEntity.ok(ApiResponseDto.success("Domain updated", DomainVo.fromEntity(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable String id, @RequestParam Long projectId) {
        boolean deleted = domainService.delete(id, projectId);
        if (!deleted) throw new BizException(404, "Domain not found");
        return ResponseEntity.ok(ApiResponseDto.success("Domain deleted", null));
    }

    @PostMapping("/bulkUpdate")
    public ResponseEntity<ApiResponseDto<Integer>> bulkUpdate(@RequestBody Map<String, Object> body) {
        Long projectId = Long.parseLong(body.get("projectId").toString());
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        String type = body.get("type") instanceof String s && !s.isBlank() ? s : null;
        String remark = body.get("remark") instanceof String s && !s.isBlank() ? s : null;
        String cdn = body.get("cdn") instanceof String s && !s.isBlank() ? s : null;
        int updated = domainService.bulkUpdate(projectId, ids, type, remark, cdn);
        return ResponseEntity.ok(ApiResponseDto.success("Updated " + updated + " domains", updated));
    }

    @PostMapping("/bulkDelete")
    public ResponseEntity<ApiResponseDto<Integer>> bulkDelete(@RequestBody Map<String, Object> body) {
        Long projectId = Long.parseLong(body.get("projectId").toString());
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        int deleted = domainService.bulkDelete(projectId, ids);
        return ResponseEntity.ok(ApiResponseDto.success("Deleted " + deleted + " domains", deleted));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponseDto<Integer>> importDomains(@RequestBody Map<String, Object> body) {
        Long projectId = Long.parseLong(body.get("projectId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) body.get("domains");
        List<DomainEntity> domains = items.stream().map(m -> {
            DomainEntity d = new DomainEntity();
            d.setDomain(m.get("domain"));
            d.setEnv(m.get("env"));
            d.setType(m.getOrDefault("type", "web"));
            d.setRemark(m.getOrDefault("remark", ""));
            d.setCdn(m.getOrDefault("cdn", ""));
            return d;
        }).toList();
        domainService.importDomains(projectId, domains);
        return ResponseEntity.ok(ApiResponseDto.success("Imported " + domains.size() + " domains", domains.size()));
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

    private List<DomainEntity> applyDomainFilter(List<DomainEntity> domains, String role) {
        if ("None".equals(role)) return List.of();
        if (!"Member".equals(role)) return domains;
        Set<String> memberProdTypes = Set.of("landingpage", "antiblock", "bucket", "web");
        return domains.stream()
                .filter(d -> !"prod".equals(d.getEnv()) || memberProdTypes.contains(d.getType()))
                .toList();
    }
}