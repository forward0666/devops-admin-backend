package com.backend.manage.controller;

import com.backend.manage.entity.ModuleEntity;
import com.backend.manage.service.ModuleService;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.utils.exception.BizException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 功能模块管理 — admin 控制台模块化菜单和权限配置
 */
@Slf4j
@RestController
@RequestMapping("/module")
@RequiredArgsConstructor
@Tag(name = "Module Management", description = "功能模块管理")
public class ModuleController {

    private final ModuleService moduleService;

    @GetMapping
    @Operation(summary = "获取模块树", description = "返回所有已启用的模块树形结构")
    public ResponseEntity<ApiResponseDto<List<ModuleEntity>>> getTree(
            @RequestParam(required = false) String category) {
        List<ModuleEntity> tree = category != null
                ? moduleService.getModuleTreeByCategory(category)
                : moduleService.getModuleTree();
        return ResponseEntity.ok(ApiResponseDto.success("success", tree));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取模块详情")
    public ResponseEntity<ApiResponseDto<ModuleEntity>> getById(@PathVariable Long id) {
        ModuleEntity module = moduleService.getModuleById(id);
        if (module == null) throw new BizException(404, "模块不存在");
        return ResponseEntity.ok(ApiResponseDto.success("success", module));
    }

    @GetMapping("/role/{roleId}")
    @Operation(summary = "获取角色可见模块")
    public ResponseEntity<ApiResponseDto<List<Long>>> getRoleModuleIds(@PathVariable Long roleId) {
        List<Long> ids = moduleService.getModuleIdsByRoleId(roleId);
        return ResponseEntity.ok(ApiResponseDto.success("success", ids));
    }

    @GetMapping("/role/{roleId}/tree")
    @Operation(summary = "获取角色可见模块树")
    public ResponseEntity<ApiResponseDto<List<ModuleEntity>>> getRoleModuleTree(@PathVariable Long roleId) {
        List<ModuleEntity> tree = moduleService.getModulesByRoleId(roleId);
        return ResponseEntity.ok(ApiResponseDto.success("success", tree));
    }

    @PostMapping
    @Operation(summary = "创建模块")
    public ResponseEntity<ApiResponseDto<ModuleEntity>> create(@RequestBody ModuleEntity module) {
        ModuleEntity created = moduleService.createModule(module);
        return ResponseEntity.ok(ApiResponseDto.success("模块创建成功", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模块")
    public ResponseEntity<ApiResponseDto<ModuleEntity>> update(
            @PathVariable Long id, @RequestBody ModuleEntity module) {
        module.setId(id);
        ModuleEntity updated = moduleService.updateModule(module);
        return ResponseEntity.ok(ApiResponseDto.success("模块更新成功", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模块")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        moduleService.deleteModule(id);
        return ResponseEntity.ok(ApiResponseDto.success("模块删除成功", null));
    }

    @PutMapping("/role/{roleId}")
    @Operation(summary = "保存角色模块权限", description = "全量替换角色的模块访问权限")
    public ResponseEntity<ApiResponseDto<Void>> saveRoleModules(
            @PathVariable Long roleId, @RequestBody List<Long> moduleIds) {
        moduleService.saveRoleModules(roleId, moduleIds);
        return ResponseEntity.ok(ApiResponseDto.success("角色模块权限保存成功", null));
    }

    @PutMapping("/{id}/sort")
    @Operation(summary = "更新模块排序")
    public ResponseEntity<ApiResponseDto<Void>> updateSort(
            @PathVariable Long id, @RequestParam Integer sortOrder) {
        // TODO: implement sort update via ModuleService
        return ResponseEntity.ok(ApiResponseDto.success("排序更新成功", null));
    }
}