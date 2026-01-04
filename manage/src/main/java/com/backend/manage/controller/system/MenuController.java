package com.backend.manage.controller.system;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.entity.system.MenuEntity;
import com.backend.manage.service.system.MenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/menu")
public class MenuController {

    @Autowired
    private MenuService menuService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<MenuEntity>>> getAllMenus() {
        log.info("GET /menu - Fetching all menus");
        try {
            var allMenus = menuService.getAllMenus();
            log.info("Successfully retrieved {} menus", allMenus.size());
            return ResponseEntity.ok(ApiResponseDto.success("Menus retrieved successfully", allMenus));
        } catch (Exception e) {
            log.error("Error retrieving allMenus", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve allMenus: " + e.getMessage()));
        }
    }

    @GetMapping("/{menuId}")
    public ResponseEntity<ApiResponseDto<MenuEntity>> getMenuById(@PathVariable Long id) {
        log.info("GET /menu/{} - Fetching menu by ID", id);
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
            log.error("Error retrieving menu by ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve menu by ID: " + e.getMessage()));
        }
    }

    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建菜单",
        resourceType = "MENU",
        description = "创建新菜单"
    )
    public ResponseEntity<ApiResponseDto<MenuEntity>> createMenu(@RequestBody MenuEntity menu) {
        log.info("POST /menu - Creating new menu: {}", menu.getName());
        try {
            var createdMenu = menuService.createMenu(menu);
            log.info("Successfully created menu by ID: {}", createdMenu.getMenuId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponseDto.success("Menu created successfully", createdMenu));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid menu data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid menu data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating menu by ID: {}", menu.getMenuId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to create menu by ID: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新菜单",
        resourceType = "MENU",
        resourceIdIndex = 0,
        description = "更新菜单信息"
    )
    public ResponseEntity<ApiResponseDto<MenuEntity>> updateMenu(@PathVariable Long id, @RequestBody MenuEntity menu) {
        log.info("PUT /menu/{} - Updating menu by ID: {}", id, menu.getName());
        try {
            menu.setMenuId(id);
            var updatedMenu = menuService.updateMenu(menu);
            if (updatedMenu != null) {
                log.info("Successfully updated menu by ID: {}", updatedMenu.getMenuId());
                return ResponseEntity.ok(ApiResponseDto.success("Menu updated successfully", updatedMenu));
            } else {
                log.warn("Menu not found for update by ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Menu not found"));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid menu data for update by ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid menu data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating menu by ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to update menu by ID: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除菜单",
        resourceType = "MENU",
        resourceIdIndex = 0,
        description = "删除菜单"
    )
    public ResponseEntity<ApiResponseDto<Void>> deleteMenu(@PathVariable Long id) {
        log.info("DELETE /menu/{} - Deleting menu by ID: {}", id, id);
        try {
            boolean deleted = menuService.deleteMenu(id);
            if (deleted) {
                log.info("Successfully deleted menu by ID: {}", id);
                return ResponseEntity.ok(ApiResponseDto.success("Menu deleted successfully", null));
            } else {
                log.warn("Menu not found for deletion by ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Menu not found"));
            }
        } catch (IllegalStateException e) {
            log.warn("Cannot delete menu by ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponseDto.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting menu by ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to delete menu by ID: " + e.getMessage()));
        }
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<ApiResponseDto<List<MenuEntity>>> getChildMenus(@PathVariable Long parentId) {
        log.info("GET /menu/parent/{} - Fetching child menus by parent ID: {}", parentId, parentId);
        try {
            var menus = menuService.getChildMenus(parentId);
            log.info("Successfully retrieved {} child menus by parent ID: {}", menus.size(), parentId);
            return ResponseEntity.ok(ApiResponseDto.success("Child menus retrieved successfully", menus));
        } catch (Exception e) {
            log.error("Error retrieving child menus by parent ID: {}", parentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve child menus by parent ID: " + e.getMessage()));
        }
    }
}
