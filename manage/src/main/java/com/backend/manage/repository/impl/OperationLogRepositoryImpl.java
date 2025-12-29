package com.backend.manage.repository.impl;

import com.backend.manage.entity.OperationLogEntity;
import com.backend.manage.repository.OperationLogRepository;
import com.backend.manage.util.MongoQueryUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Repository
public class OperationLogRepositoryImpl implements OperationLogRepository {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public OperationLogRepositoryImpl(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询操作日志
     */
    @Override
    public Page<OperationLogEntity> findOperationLogs(int page, int size, String sortBy, String sortDir) {
        try {
            InputStream input = getClass().getResourceAsStream("/mongo/operationLogRepository.json");
            JsonNode root = objectMapper.readTree(input);
            JsonNode node = root.get("findOperationLogs");

            // 构建查询
            Query query = MongoQueryUtil.buildQueryFromJson(node.get("filter"), sortBy, sortDir, null);

            // 分页
            query.skip(page * size).limit(size);
            List<OperationLogEntity> logs = mongoTemplate.find(query, OperationLogEntity.class);

            // 总数统计
            Query countQuery = MongoQueryUtil.buildQueryFromJson(node.get("filter"), null, null, null);
            long total = mongoTemplate.count(countQuery, OperationLogEntity.class);
            log.info("✅findOperationLogs: page={}, size={}, sortBy={}, sortDir={}", page, size, sortBy, sortDir);
            Pageable pageable = PageRequest.of(page, size,
                    Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy));

            return new PageImpl<>(logs, pageable, total);


        } catch (Exception e) {
            throw new RuntimeException("Failed to read operationLogRepository.json", e);
        }
    }

    /**
     * 获取最近 5 条操作日志
     */
    @Override
    public List<OperationLogEntity> findTop5ByOrderByCreatedAtDesc() {
        List<OperationLogEntity> logs = findOperationLogs(0, 5, "createdAt", "desc").getContent();
        log.info("✅findTop5ByOrderByCreatedAtDesc - fetched {} logs: {}", logs.size(), logs);
        return logs;
    }

    /**
     * 保存操作日志
     */
    @Override
    public OperationLogEntity save(OperationLogEntity operationLog) {
        return mongoTemplate.save(operationLog);
    }
}
