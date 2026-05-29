package com.backend.manage.mapper;

import com.backend.manage.entity.OperationLogEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OperationLogMapper {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    private static final String COLLECTION = "operation_logs";
    private static final String QUERY_FILE = "mongo/OperationLogRepository.json";

    private JsonNode queryDefinitions;

    @PostConstruct
    public void init() {
        try {
            InputStream is = new ClassPathResource(QUERY_FILE).getInputStream();
            queryDefinitions = objectMapper.readTree(is);
            log.info("Loaded MongoDB query definitions from {}", QUERY_FILE);
        } catch (Exception e) {
            log.error("Failed to load query definitions from {}: {}", QUERY_FILE, e.getMessage());
            queryDefinitions = objectMapper.createObjectNode();
        }
    }

    // ==================== Query Template ====================

    private Query buildQueryFromTemplate(String templateName) {
        Query query = new Query();
        JsonNode def = queryDefinitions.get(templateName);
        if (def == null) return query;

        JsonNode sortNode = def.get("sort");
        if (sortNode != null && sortNode.isObject()) {
            List<Sort.Order> orders = new ArrayList<>();
            sortNode.fields().forEachRemaining(entry -> {
                int direction = entry.getValue().asInt(-1);
                orders.add(new Sort.Order(direction == 1 ? Sort.Direction.ASC : Sort.Direction.DESC, entry.getKey()));
            });
            if (!orders.isEmpty()) {
                query.with(Sort.by(orders));
            }
        }

        return query;
    }

    // ==================== Insert ====================

    public void insert(OperationLogEntity entity) {
        mongoTemplate.save(entity, COLLECTION);
    }

    // ==================== Select ====================

    public record QueryResult(List<OperationLogEntity> logs, long total) {}

    public QueryResult findByCriteriaWithTotal(String category, String startDate, String endDate,
                                                int page, int size, String sortBy, String sortDir) {
        Query query = buildCriteriaQuery(category, startDate, endDate);

        // Count total (use raw collection to avoid _class filtering)
        long total = mongoTemplate.count(query, COLLECTION);

        // Sort
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort.Direction dir = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
            query.with(Sort.by(dir, sortBy));
        } else {
            query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        // Pagination
        query.skip((long) page * size).limit(size);

        // Use BasicDBObject to avoid _class filtering
        List<org.bson.Document> docs = mongoTemplate.find(query, org.bson.Document.class, COLLECTION);
        List<OperationLogEntity> logs = docs.stream().map(doc -> {
            OperationLogEntity entity = new OperationLogEntity();
            entity.setId(doc.getObjectId("_id") != null ? doc.getObjectId("_id").toString() : null);
            entity.setOperationId(doc.getString("operationId"));
            Object userId = doc.get("userId");
            if (userId instanceof Number) entity.setUserId(((Number) userId).longValue());
            entity.setUsername(doc.getString("username"));
            entity.setOperationType(doc.getString("operationType"));
            entity.setOperationName(doc.getString("operationName"));
            entity.setResourceType(doc.getString("resourceType"));
            entity.setResourceId(doc.getString("resourceId"));
            entity.setMethod(doc.getString("method"));
            entity.setUrl(doc.getString("url"));
            entity.setIpAddress(doc.getString("ipAddress"));
            entity.setUserAgent(doc.getString("userAgent"));
            entity.setStatus(doc.getString("status"));
            entity.setErrorMessage(doc.getString("errorMessage"));
            entity.setCategory(doc.getString("category"));
            entity.setCreatedAt(doc.getDate("createdAt") != null ? doc.getDate("createdAt").toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : null);
            return entity;
        }).toList();
        return new QueryResult(logs, total);
    }

    public List<OperationLogEntity> findRecent(int limit) {
        Query query = buildQueryFromTemplate("findRecentLogs");
        query.limit(limit);
        List<org.bson.Document> docs = mongoTemplate.find(query, org.bson.Document.class, COLLECTION);
        return docs.stream().map(doc -> {
            OperationLogEntity entity = new OperationLogEntity();
            entity.setId(doc.getObjectId("_id") != null ? doc.getObjectId("_id").toString() : null);
            entity.setOperationId(doc.getString("operationId"));
            Object userId = doc.get("userId");
            if (userId instanceof Number) entity.setUserId(((Number) userId).longValue());
            entity.setUsername(doc.getString("username"));
            entity.setOperationType(doc.getString("operationType"));
            entity.setOperationName(doc.getString("operationName"));
            entity.setResourceType(doc.getString("resourceType"));
            entity.setResourceId(doc.getString("resourceId"));
            entity.setMethod(doc.getString("method"));
            entity.setUrl(doc.getString("url"));
            entity.setIpAddress(doc.getString("ipAddress"));
            entity.setUserAgent(doc.getString("userAgent"));
            entity.setStatus(doc.getString("status"));
            entity.setErrorMessage(doc.getString("errorMessage"));
            entity.setCategory(doc.getString("category"));
            entity.setCreatedAt(doc.getDate("createdAt") != null ? doc.getDate("createdAt").toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : null);
            return entity;
        }).toList();
    }

    // ==================== Private Helpers ====================

    private Query buildCriteriaQuery(String category, String startDate, String endDate) {
        Query query = new Query();

        if (category != null && !category.isEmpty()) {
            query.addCriteria(Criteria.where("category").is(category));
        }

        if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
            Criteria dateCriteria = Criteria.where("createdAt");
            if (startDate != null && !startDate.isEmpty()) {
                dateCriteria.gte(LocalDate.parse(startDate).atStartOfDay());
            }
            if (endDate != null && !endDate.isEmpty()) {
                dateCriteria.lt(LocalDate.parse(endDate).atStartOfDay().plusDays(1));
            }
            query.addCriteria(dateCriteria);
        }

        return query;
    }
}
