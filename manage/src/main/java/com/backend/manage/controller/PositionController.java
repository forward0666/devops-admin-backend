package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponse;
import com.backend.manage.model.Position;
import com.backend.manage.service.PositionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 职位管理控制器
 * 处理职位相关的CRUD操作和业务逻辑
 */
@RestController
@RequestMapping("/positions")
public class PositionController {

    private static final Logger logger = LoggerFactory.getLogger(PositionController.class);

    @Autowired
    private PositionService positionService;

    /**
     * 获取所有职位列表接口
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Position>>> getAllPositions() {
        logger.info("GET /positions - Fetching all positions");
        try {
            List<Position> positions = positionService.getAllPositions();
            logger.info("Successfully retrieved {} positions", positions.size());
            return ResponseEntity.ok(ApiResponse.success("Positions retrieved successfully", positions));
        } catch (Exception e) {
            logger.error("Error retrieving positions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve positions: " + e.getMessage()));
        }
    }

    /**
     * 根据ID获取职位详情接口
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Position>> getPositionById(@PathVariable Long id) {
        logger.info("GET /positions/{} - Fetching position by ID", id);
        try {
            Position position = positionService.getPositionById(id);
            if (position != null) {
                logger.info("Successfully retrieved position: {}", position.getName());
                return ResponseEntity.ok(ApiResponse.success("Position retrieved successfully", position));
            } else {
                logger.warn("Position not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Position not found"));
            }
        } catch (Exception e) {
            logger.error("Error retrieving position with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve position: " + e.getMessage()));
        }
    }

    /**
     * 创建新职位接口
     */
    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建职位",
        resourceType = "POSITION",
        description = "创建新职位"
    )
    public ResponseEntity<ApiResponse<Position>> createPosition(@Valid @RequestBody Position position) {
        logger.info("POST /positions - Creating new position: {}", position.getName());
        try {
            Position createdPosition = positionService.createPosition(position);
            logger.info("Successfully created position with ID: {}", createdPosition.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Position created successfully", createdPosition));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid position data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid position data: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating position: {}", position.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create position: " + e.getMessage()));
        }
    }

    /**
     * 更新职位信息接口
     */
    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新职位",
        resourceType = "POSITION",
        resourceIdIndex = 0,
        description = "更新职位信息"
    )
    public ResponseEntity<ApiResponse<Position>> updatePosition(@PathVariable Long id, @Valid @RequestBody Position position) {
        logger.info("PUT /positions/{} - Updating position", id);
        try {
            position.setId(id);
            Position updatedPosition = positionService.updatePosition(position);
            if (updatedPosition != null) {
                logger.info("Successfully updated position: {}", updatedPosition.getName());
                return ResponseEntity.ok(ApiResponse.success("Position updated successfully", updatedPosition));
            } else {
                logger.warn("Position not found for update with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Position not found"));
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid position data for update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid position data: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating position with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update position: " + e.getMessage()));
        }
    }

    /**
     * 删除职位接口
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除职位",
        resourceType = "POSITION",
        resourceIdIndex = 0,
        description = "删除职位"
    )
    public ResponseEntity<ApiResponse<Void>> deletePosition(@PathVariable Long id) {
        logger.info("DELETE /positions/{} - Deleting position", id);
        try {
            boolean deleted = positionService.deletePosition(id);
            if (deleted) {
                logger.info("Successfully deleted position with ID: {}", id);
                return ResponseEntity.ok(ApiResponse.success("Position deleted successfully", null));
            } else {
                logger.warn("Position not found for deletion with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Position not found"));
            }
        } catch (IllegalStateException e) {
            logger.warn("Cannot delete position with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting position with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete position: " + e.getMessage()));
        }
    }

    /**
     * 根据部门ID获取职位列表接口
     */
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<ApiResponse<List<Position>>> getPositionsByDepartmentId(@PathVariable Long departmentId) {
        logger.info("GET /positions/department/{} - Fetching positions by department ID", departmentId);
        try {
            List<Position> positions = positionService.getPositionsByDepartmentId(departmentId);
            logger.info("Successfully retrieved {} positions for department ID: {}", positions.size(), departmentId);
            return ResponseEntity.ok(ApiResponse.success("Positions retrieved successfully", positions));
        } catch (Exception e) {
            logger.error("Error retrieving positions for department ID: {}", departmentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve positions: " + e.getMessage()));
        }
    }
}
