package com.backend.manage.repository;

import com.backend.manage.entity.OperationLogEntity;
import org.springframework.data.domain.Page;
import java.util.List;

public interface OperationLogRepository {
    /**
     * 分页查询操作日志
     *
     * @param page    页码，从 0 开始
     * @param size    每页数量
     * @param sortBy  排序字段
     * @param sortDir 排序方向，asc 或 desc
     * @return 分页结果
     */
    Page<OperationLogEntity> findOperationLogs(int page, int size, String sortBy, String sortDir);
    List<OperationLogEntity> findTop5ByOrderByCreatedAtDesc(); // 获取最近操作日志

    OperationLogEntity save(OperationLogEntity operationLog);
}