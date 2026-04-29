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

        Query query = new Query(Criteria.where("projectId").is(projectId));

        if (template != null && template.containsKey("sort")) {
            Map<String, Object> sortMap = (Map<String, Object>) template.get("sort");
            for (Map.Entry<String, Object> entry : sortMap.entrySet()) {
                query.with(Sort.by(entry.getValue().equals(-1) ? Sort.Direction.DESC : Sort.Direction.ASC, entry.getKey()));
            }
        } else {
            query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        return mongoTemplate.find(query, DomainEntity.class, collection);
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
