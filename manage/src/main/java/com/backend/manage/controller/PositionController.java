package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.entity.PositionEntity;
import com.backend.manage.service.PositionService;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequestMapping("/positions")
public class PositionController {

    @Autowired
    private PositionService positionService;

    /**
     * 获取所有职位列表接口
     */
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<PositionEntity>>> getAllPositions() {
        log.info("GET /positions - Fetching all positions");
        try {
            List<PositionEntity> positions = positionService.getAllPositions();
            log.info("Successfully retrieved {} positions", positions.size());
            return ResponseEntity.ok(ApiResponseDto.success("Positions retrieved successfully", positions));
        } catch (Exception e) {
            log.error("Error retrieving positions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve positions: " + e.getMessage()));
        }
    }

    /**
     * 根据ID获取职位详情接口
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<PositionEntity>> getPositionById(@PathVariable Long id) {
        log.info("GET /positions/{} - Fetching position by ID", id);
        try {
            PositionEntity position = positionService.getPositionById(id);
            if (position != null) {
                log.info("Successfully retrieved position: {}", position.getName());
                return ResponseEntity.ok(ApiResponseDto.success("Position retrieved successfully", position));
            } else {
                log.warn("Position not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Position not found"));
            }
        } catch (Exception e) {
            log.error("Error retrieving position with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve position: " + e.getMessage()));
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
    public ResponseEntity<ApiResponseDto<PositionEntity>> createPosition(@Valid @RequestBody PositionEntity position) {
        log.info("POST /positions - Creating new position: {}", position.getName());
        try {
            PositionEntity createdPosition = positionService.createPosition(position);
            log.info("Successfully created position with ID: {}", createdPosition.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponseDto.success("Position created successfully", createdPosition));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid position data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid position data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating position: {}", position.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to create position: " + e.getMessage()));
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
    public ResponseEntity<ApiResponseDto<PositionEntity>> updatePosition(@PathVariable Long id, @Valid @RequestBody PositionEntity position) {
        log.info("PUT /positions/{} - Updating position", id);
        try {
            position.setId(id);
            PositionEntity updatedPosition = positionService.updatePosition(position);
            if (updatedPosition != null) {
                log.info("Successfully updated position: {}", updatedPosition.getName());
                return ResponseEntity.ok(ApiResponseDto.success("Position updated successfully", updatedPosition));
            } else {
                log.warn("Position not found for update with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Position not found"));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid position data for update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid position data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating position with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to update position: " + e.getMessage()));
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
    public ResponseEntity<ApiResponseDto<Void>> deletePosition(@PathVariable Long id) {
        log.info("DELETE /positions/{} - Deleting position", id);
        try {
            boolean deleted = positionService.deletePosition(id);
            if (deleted) {
                log.info("Successfully deleted position with ID: {}", id);
                return ResponseEntity.ok(ApiResponseDto.success("Position deleted successfully", null));
            } else {
                log.warn("Position not found for deletion with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Position not found"));
            }
        } catch (IllegalStateException e) {
            log.warn("Cannot delete position with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponseDto.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting position with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to delete position: " + e.getMessage()));
        }
    }

    /**
     * 根据部门ID获取职位列表接口
     */
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<ApiResponseDto<List<PositionEntity>>> getPositionsByDepartmentId(@PathVariable Long departmentId) {
        log.info("GET /positions/department/{} - Fetching positions by department ID", departmentId);
        try {
            List<PositionEntity> positions = positionService.getPositionsByDepartmentId(departmentId);
            log.info("Successfully retrieved {} positions for department ID: {}", positions.size(), departmentId);
            return ResponseEntity.ok(ApiResponseDto.success("Positions retrieved successfully", positions));
        } catch (Exception e) {
            log.error("Error retrieving positions for department ID: {}", departmentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve positions: " + e.getMessage()));
        }
    }
}
