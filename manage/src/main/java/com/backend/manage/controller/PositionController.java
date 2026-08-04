package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.manage.entity.PositionEntity;
import com.backend.manage.service.PositionService;
import com.backend.manage.vo.PositionVo;
import com.backend.utils.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/position")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<PositionVo>>> getAllPositions() {
        List<PositionEntity> positions = positionService.getAllPositions();
        List<PositionVo> result = positions.stream().map(PositionVo::fromEntity).toList();
        return ResponseEntity.ok(ApiResponseDto.success("Positions retrieved successfully", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<PositionVo>> getPositionById(@PathVariable Long id) {
        PositionEntity position = positionService.getPositionById(id);
        if (position == null) throw new BizException(404, "Position not found");
        return ResponseEntity.ok(ApiResponseDto.success("Position retrieved successfully", PositionVo.fromEntity(position)));
    }

    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建职位",
        resourceType = "POSITION",
        description = "创建新职位"
    )
    public ResponseEntity<ApiResponseDto<PositionVo>> createPosition(@Valid @RequestBody PositionEntity position) {
        PositionEntity created = positionService.createPosition(position);
        return ResponseEntity.ok(ApiResponseDto.success("Position created successfully", PositionVo.fromEntity(created)));
    }

    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新职位",
        resourceType = "POSITION",
        resourceIdIndex = 0,
        description = "更新职位信息"
    )
    public ResponseEntity<ApiResponseDto<PositionVo>> updatePosition(@PathVariable Long id, @Valid @RequestBody PositionEntity position) {
        position.setId(id);
        PositionEntity updated = positionService.updatePosition(position);
        if (updated == null) throw new BizException(404, "Position not found");
        return ResponseEntity.ok(ApiResponseDto.success("Position updated successfully", PositionVo.fromEntity(updated)));
    }

    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除职位",
        resourceType = "POSITION",
        resourceIdIndex = 0,
        description = "删除职位"
    )
    public ResponseEntity<ApiResponseDto<Void>> deletePosition(@PathVariable Long id) {
        boolean deleted = positionService.deletePosition(id);
        if (!deleted) throw new BizException(404, "Position not found");
        return ResponseEntity.ok(ApiResponseDto.success("Position deleted successfully", null));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<ApiResponseDto<List<PositionVo>>> getPositionsByDepartmentId(@PathVariable Long departmentId) {
        List<PositionEntity> positions = positionService.getPositionsByDepartmentId(departmentId);
        List<PositionVo> result = positions.stream().map(PositionVo::fromEntity).toList();
        return ResponseEntity.ok(ApiResponseDto.success("Positions retrieved successfully", result));
    }
}