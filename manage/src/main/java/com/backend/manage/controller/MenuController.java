package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponse;
import com.backend.manage.model.Menu;
import com.backend.manage.service.MenuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理控制器
 * 处理菜单相关的CRUD操作和业务逻辑
 */
@RestController
@RequestMapping("/menus")
public class MenuController {

    private static final Logger logger = LoggerFactory.getLogger(MenuController.class);

    @Autowired
    private MenuService menuService;

    /**
     * 获取所有菜单列表接口
     * @return ResponseEntity包含操作结果
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Menu>>> getAllMenus() {
        logger.info("GET /menus - Fetching all menus");
        try {
            List<Menu> menus = menuService.getAllMenus();
            logger.info("Successfully retrieved {} menus", menus.size());
            return ResponseEntity.ok(ApiResponse.success("Menus retrieved successfully", menus));
        } catch (Exception e) {
            logger.error("Error retrieving menus", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve menus: " + e.getMessage()));
        }
    }

    /**
     * 根据ID获取菜单详情接口
     * @param id 菜单ID
     * @return ResponseEntity包含操作结果
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Menu>> getMenuById(@PathVariable Long id) {
        logger.info("GET /menus/{} - Fetching menu by ID", id);
        try {
            Menu menu = menuService.getMenuById(id);
            if (menu != null) {
                logger.info("Successfully retrieved menu: {}", menu.getName());
                return ResponseEntity.ok(ApiResponse.success("Menu retrieved successfully", menu));
            } else {
                logger.warn("Menu not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Menu not found"));
            }
        } catch (Exception e) {
            logger.error("Error retrieving menu with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve menu: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Menu>> createMenu(@RequestBody Menu menu) {
        logger.info("POST /menus - Creating new menu: {}", menu.getName());
        try {
            Menu createdMenu = menuService.createMenu(menu);
            logger.info("Successfully created menu with ID: {}", createdMenu.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Menu created successfully", createdMenu));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid menu data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid menu data: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating menu: {}", menu.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create menu: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Menu>> updateMenu(@PathVariable Long id, @RequestBody Menu menu) {
        logger.info("PUT /menus/{} - Updating menu", id);
        try {
            menu.setId(id);
            Menu updatedMenu = menuService.updateMenu(menu);
            if (updatedMenu != null) {
                logger.info("Successfully updated menu: {}", updatedMenu.getName());
                return ResponseEntity.ok(ApiResponse.success("Menu updated successfully", updatedMenu));
            } else {
                logger.warn("Menu not found for update with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Menu not found"));
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid menu data for update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid menu data: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating menu with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update menu: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Void>> deleteMenu(@PathVariable Long id) {
        logger.info("DELETE /menus/{} - Deleting menu", id);
        try {
            boolean deleted = menuService.deleteMenu(id);
            if (deleted) {
                logger.info("Successfully deleted menu with ID: {}", id);
                return ResponseEntity.ok(ApiResponse.success("Menu deleted successfully", null));
            } else {
                logger.warn("Menu not found for deletion with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Menu not found"));
            }
        } catch (IllegalStateException e) {
            logger.warn("Cannot delete menu with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting menu with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete menu: " + e.getMessage()));
        }
    }

    /**
     * 获取根菜单列表接口
     * @return ResponseEntity包含操作结果
     */
    @GetMapping("/root")
    public ResponseEntity<ApiResponse<List<Menu>>> getRootMenus() {
        logger.info("GET /menus/root - Fetching root menus");
        try {
            List<Menu> menus = menuService.getRootMenus();
            logger.info("Successfully retrieved {} root menus", menus.size());
            return ResponseEntity.ok(ApiResponse.success("Root menus retrieved successfully", menus));
        } catch (Exception e) {
            logger.error("Error retrieving root menus", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve root menus: " + e.getMessage()));
        }
    }

    /**
     * 根据父菜单ID获取子菜单接口
     * @param parentId 父菜单ID
     * @return ResponseEntity包含操作结果
     */
    @GetMapping("/parent/{parentId}")
    public ResponseEntity<ApiResponse<List<Menu>>> getChildMenus(@PathVariable Long parentId) {
        logger.info("GET /menus/parent/{} - Fetching child menus", parentId);
        try {
            List<Menu> menus = menuService.getChildMenus(parentId);
            logger.info("Successfully retrieved {} child menus", menus.size());
            return ResponseEntity.ok(ApiResponse.success("Child menus retrieved successfully", menus));
        } catch (Exception e) {
            logger.error("Error retrieving child menus for parent ID: {}", parentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve child menus: " + e.getMessage()));
        }
    }
}
