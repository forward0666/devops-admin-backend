package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.entity.MenuEntity;
import com.backend.manage.service.MenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理控制器
 * 处理菜单相关的CRUD操作和业务逻辑
 * 使用 Java 21 风格
 */
@Slf4j
@RestController
@RequestMapping("/menus")
public class MenuController {

    @Autowired
    private MenuService menuService;

    /**
     * 获取所有菜单列表接口
     * @return ResponseEntity包含操作结果
     */
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<MenuEntity>>> getAllMenus() {
        log.info("GET /menus - Fetching all menus");
        try {
            var menus = menuService.getAllMenus();
            log.info("Successfully retrieved {} menus", menus.size());
            return ResponseEntity.ok(ApiResponseDto.success("Menus retrieved successfully", menus));
        } catch (Exception e) {
            log.error("Error retrieving menus", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve menus: " + e.getMessage()));
        }
    }

    /**
     * 根据ID获取菜单详情接口
     * @param id 菜单ID（可以是内部 id 或 directoryId）
     * @return ResponseEntity包含操作结果
     */
    @GetMapping("/{menuId}")
    public ResponseEntity<ApiResponseDto<MenuEntity>> getMenuById(@PathVariable Long id) {
        log.info("GET /menus/{} - Fetching menu by ID", id);
        try {
            var menu = menuService.getMenuById(id);
            if (menu != null) {
                log.info("Successfully retrieved menu: {}, id: {}", menu.getName(), menu.getMenuId());
                return ResponseEntity.ok(ApiResponseDto.success("Menu retrieved successfully", menu));
            } else {
                log.warn("Menu not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Menu not found"));
            }
        } catch (Exception e) {
            log.error("Error retrieving menu with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve menu: " + e.getMessage()));
        }
    }

    /**
     * 创建新菜单接口
     * @param menu 菜单对象
     * @return ResponseEntity包含操作结果
     */
    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建菜单",
        resourceType = "MENU",
        description = "创建新菜单"
    )
    public ResponseEntity<ApiResponseDto<MenuEntity>> createMenu(@RequestBody MenuEntity menu) {
        log.info("POST /menus - Creating new menu: {}", menu.getName());
        try {
            var createdMenu = menuService.createMenu(menu);
            log.info("Successfully created menu with ID: {}", createdMenu.getMenuId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponseDto.success("Menu created successfully", createdMenu));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid menu data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid menu data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating menu: {}", menu.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to create menu: " + e.getMessage()));
        }
    }

    /**
     * 更新菜单信息接口
     * @param id 菜单ID
     * @param menu 菜单对象
     * @return ResponseEntity包含操作结果
     */
    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新菜单",
        resourceType = "MENU",
        resourceIdIndex = 0,
        description = "更新菜单信息"
    )
    public ResponseEntity<ApiResponseDto<MenuEntity>> updateMenu(@PathVariable Long id, @RequestBody MenuEntity menu) {
        log.info("PUT /menus/{} - Updating menu", id);
        try {
            menu.setMenuId(id);
            var updatedMenu = menuService.updateMenu(menu);
            if (updatedMenu != null) {
                log.info("Successfully updated menu: {}", updatedMenu.getName());
                return ResponseEntity.ok(ApiResponseDto.success("Menu updated successfully", updatedMenu));
            } else {
                log.warn("Menu not found for update with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Menu not found"));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid menu data for update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid menu data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating menu with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to update menu: " + e.getMessage()));
        }
    }

    /**
     * 删除菜单接口
     * @param id 菜单ID
     * @return ResponseEntity包含操作结果
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除菜单",
        resourceType = "MENU",
        resourceIdIndex = 0,
        description = "删除菜单"
    )
    public ResponseEntity<ApiResponseDto<Void>> deleteMenu(@PathVariable Long id) {
        log.info("DELETE /menus/{} - Deleting menu", id);
        try {
            boolean deleted = menuService.deleteMenu(id);
            if (deleted) {
                log.info("Successfully deleted menu with ID: {}", id);
                return ResponseEntity.ok(ApiResponseDto.success("Menu deleted successfully", null));
            } else {
                log.warn("Menu not found for deletion with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Menu not found"));
            }
        } catch (IllegalStateException e) {
            log.warn("Cannot delete menu with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponseDto.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting menu with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to delete menu: " + e.getMessage()));
        }
    }

    /**
     * 获取根菜单列表接口
     * @return ResponseEntity包含操作结果
     */
    @GetMapping("/root")
    public ResponseEntity<ApiResponseDto<List<MenuEntity>>> getRootMenus() {
        log.info("GET /menus/root - Fetching root menus");
        try {
            var menus = menuService.getRootMenus();
            log.info("Successfully retrieved {} root menus", menus.size());
            return ResponseEntity.ok(ApiResponseDto.success("Root menus retrieved successfully", menus));
        } catch (Exception e) {
            log.error("Error retrieving root menus", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve root menus: " + e.getMessage()));
        }
    }

    /**
     * 根据父菜单ID获取子菜单接口
     * @param parentId 父菜单ID
     * @return ResponseEntity包含操作结果
     */
    @GetMapping("/parent/{parentId}")
    public ResponseEntity<ApiResponseDto<List<MenuEntity>>> getChildMenus(@PathVariable Long parentId) {
        log.info("GET /menus/parent/{} - Fetching child menus", parentId);
        try {
            var menus = menuService.getChildMenus(parentId);
            log.info("Successfully retrieved {} child menus", menus.size());
            return ResponseEntity.ok(ApiResponseDto.success("Child menus retrieved successfully", menus));
        } catch (Exception e) {
            log.error("Error retrieving child menus for parent ID: {}", parentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve child menus: " + e.getMessage()));
        }
    }
}
