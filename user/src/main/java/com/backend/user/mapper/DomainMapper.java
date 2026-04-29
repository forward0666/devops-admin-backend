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

    @PostConstruct
    public void init() {
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
        Map<String, Object> template = templates != null ? templates.get("findByProjectId") : null;
        String collection = template != null ? (String) template.get("collection") : "domains";

        if (template != null && template.containsKey("filter")) {
            Query query = buildQuery("findByProjectId", Map.of("projectId", projectId));
            return mongoTemplate.find(query, DomainEntity.class, collection);
        }

        // Fallback if no template
        Query query = new Query(Criteria.where("projectId").is(projectId))
            .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, DomainEntity.class, collection);
    }

    @SuppressWarnings("unchecked")
    private Query buildQuery(String templateName, Map<String, Object> params) {
        Map<String, Object> template = templates.get(templateName);
        if (template == null) return null;

        Query query = new Query();

        // Build filter from JSON
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

        // Build sort from JSON
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

    public DomainEntity findById(String id) {
        return mongoTemplate.findById(id, DomainEntity.class, "domains");
    }

    public DomainEntity save(DomainEntity entity) {
        mongoTemplate.save(entity, "domains");
        return entity;
    }

    public void saveAll(List<DomainEntity> entities) {
        mongoTemplate.insertAll(entities);
    }

    public void deleteById(String id) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(id)), "domains");
    }
}
