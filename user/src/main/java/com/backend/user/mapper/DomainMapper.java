package com.backend.user.mapper;

import com.backend.user.entity.DomainEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class DomainMapper {

    @Autowired
    @Qualifier("projectMongoTemplate")
    private MongoTemplate mongoTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, Map<String, Object>> templates;

    private static final String DEFAULT_COLLECTION = "domains";

    @PostConstruct
    public void init() {
        try {
            // 确保域名唯一复合索引
            mongoTemplate.indexOps(DEFAULT_COLLECTION).ensureIndex(
                new org.springframework.data.mongodb.core.index.CompoundIndexDefinition(
                    new org.bson.Document().append("projectId", 1).append("domain", 1)
                ).unique()
            );
        } catch (Exception e) {
            log.warn("域名唯一索引创建失败(可能已存在): {}", e.getMessage());
        }
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("mongo/domain.json");
            if (is != null) {
                templates = objectMapper.readValue(is, new TypeReference<>() {});
                log.info("Domain mapper templates loaded: {}", templates.keySet());
            }
        } catch (Exception e) {
            log.error("Failed to load domain mapper templates", e);
        }
    }

    public List<DomainEntity> findByProjectId(Long projectId) {
        Query query = buildQuery("findByProjectId", Map.of("projectId", projectId));
        return mongoTemplate.find(query, DomainEntity.class, getCollection("findByProjectId"));
    }

    public DomainEntity findById(String id) {
        Query query = buildQuery("findById", Map.of("id", id));
        return mongoTemplate.findOne(query, DomainEntity.class, getCollection("findById"));
    }

    public DomainEntity findByIdAndProjectId(String id, Long projectId) {
        Query query = buildQuery("findByIdAndProjectId", Map.of("id", id, "projectId", projectId));
        return mongoTemplate.findOne(query, DomainEntity.class, getCollection("findByIdAndProjectId"));
    }

    public DomainEntity insert(DomainEntity entity) {
        mongoTemplate.insert(entity, DEFAULT_COLLECTION);
        return entity;
    }

    public DomainEntity update(String id, Long projectId, Map<String, Object> fields) {
        Query query = buildQuery("findByIdAndProjectId", Map.of("id", id, "projectId", projectId));
        Update update = new Update();
        fields.forEach(update::set);
        update.set("updatedAt", java.time.LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, DomainEntity.class, DEFAULT_COLLECTION);
        return mongoTemplate.findOne(query, DomainEntity.class, DEFAULT_COLLECTION);
    }

    public void deleteById(String id) {
        Query query = buildQuery("findById", Map.of("id", id));
        mongoTemplate.remove(query, DomainEntity.class, getCollection("findById"));
    }

    public void insertAll(List<DomainEntity> entities) {
        // 按 projectId+domain 去重
        Map<String, DomainEntity> uniqueMap = new java.util.LinkedHashMap<>();
        for (DomainEntity e : entities) {
            String key = e.getProjectId() + ":" + e.getDomain();
            uniqueMap.putIfAbsent(key, e);
        }
        mongoTemplate.insertAll(new java.util.ArrayList<>(uniqueMap.values()));
    }

    public DomainEntity findByProjectIdAndDomain(Long projectId, String domain) {
        Query query = Query.query(Criteria.where("projectId").is(projectId).and("domain").is(domain));
        return mongoTemplate.findOne(query, DomainEntity.class, DEFAULT_COLLECTION);
    }

    public List<DomainEntity> findByProjectIdAndDomains(Long projectId, List<String> domainList) {
        Query query = Query.query(Criteria.where("projectId").is(projectId).and("domain").in(domainList));
        return mongoTemplate.find(query, DomainEntity.class, DEFAULT_COLLECTION);
    }

    public String getCollection(String templateName) {
        if (templates != null && templates.containsKey(templateName)) {
            return (String) templates.get(templateName).get("collection");
        }
        return DEFAULT_COLLECTION;
    }

    @SuppressWarnings("unchecked")
    public Query buildQuery(String templateName, Map<String, Object> params) {
        if (templates == null || !templates.containsKey(templateName)) {
            return new Query();
        }

        Map<String, Object> template = templates.get(templateName);
        Query query = new Query();

        List<Map<String, Object>> filters = (List<Map<String, Object>>) template.get("filter");
        if (filters != null) {
            for (Map<String, Object> f : filters) {
                String field = (String) f.get("field");
                String operator = (String) f.get("operator");
                Object value = params.get(f.get("param"));

                Criteria criteria = switch (operator) {
                    case "is" -> Criteria.where(field).is(value);
                    case "ne" -> Criteria.where(field).ne(value);
                    case "gte" -> Criteria.where(field).gte(value);
                    case "lte" -> Criteria.where(field).lte(value);
                    case "gt" -> Criteria.where(field).gt(value);
                    case "lt" -> Criteria.where(field).lt(value);
                    case "regex" -> Criteria.where(field).regex((String) value);
                    case "in" -> Criteria.where(field).in((List<?>) value);
                    default -> Criteria.where(field).is(value);
                };
                query.addCriteria(criteria);
            }
        }

        Map<String, Object> sortMap = (Map<String, Object>) template.get("sort");
        if (sortMap != null) {
            for (Map.Entry<String, Object> entry : sortMap.entrySet()) {
                query.with(Sort.by(
                    entry.getValue() instanceof Number && ((Number) entry.getValue()).intValue() == -1
                        ? Sort.Direction.DESC : Sort.Direction.ASC,
                    entry.getKey()
                ));
            }
        }

        return query;
    }
}
