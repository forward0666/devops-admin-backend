package com.backend.cloudflare.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.cloudflare.entity.DomainGroupEntity;
import com.backend.cloudflare.service.DomainGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/cloudflare/domainGroup")
@RequiredArgsConstructor
public class DomainGroupController {

    private final DomainGroupService domainGroupService;

    @GetMapping
    public ApiResponseDto<List<DomainGroupEntity>> list() {
        try {
            List<DomainGroupEntity> groups = domainGroupService.listAll();
            return ApiResponseDto.success("ok", groups);
        } catch (Exception e) {
            log.error("List domain groups failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("List failed: " + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponseDto<DomainGroupEntity> create(@RequestBody DomainGroupEntity entity) {
        try {
            DomainGroupEntity result = domainGroupService.create(entity);
            return ApiResponseDto.success("Domain group created", result);
        } catch (Exception e) {
            log.error("Create domain group failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Create failed: " + e.getMessage());
        }
    }

    @PutMapping
    public ApiResponseDto<DomainGroupEntity> update(@RequestBody DomainGroupEntity entity) {
        try {
            DomainGroupEntity result = domainGroupService.update(entity);
            return ApiResponseDto.success("Domain group updated", result);
        } catch (Exception e) {
            log.error("Update domain group failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Update failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponseDto<Void> delete(@PathVariable Long id) {
        try {
            domainGroupService.delete(id);
            return ApiResponseDto.success("Domain group deleted", null);
        } catch (Exception e) {
            log.error("Delete domain group failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Delete failed: " + e.getMessage());
        }
    }
}