package com.backend.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class DomainService {

    private final MongoTemplate mongoTemplate;

    public DomainService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** List zones from cloudflare MongoDB (account_{id}_zones collections) */
    public List<Map<String, Object>> listZones() {
        var collections = mongoTemplate.getCollectionNames();
        List<Map<String, Object>> all = new ArrayList<>();
        for (String colName : collections) {
            if (colName.endsWith("_zones")) {
                var query = new Query();
                query.fields()
                    .include("zone_id").include("name")
                    .include("status").include("account_id")
                    .exclude("_id");
                var docs = mongoTemplate.find(query, Map.class, colName);
                for (var doc : docs) {
                    if (doc.get("zone_id") != null && doc.get("name") != null) {
                        doc.put("accountName", "Account " + doc.get("account_id"));
                        all.add(doc);
                    }
                }
            }
        }
        all.sort(Comparator.comparing(o -> (String) o.getOrDefault("name", "")));
        return all;
    }

    // Groups CRUD via domain_group collection
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listGroups() {
        List<Map<String, Object>> docs = (List<Map<String, Object>>) (List<?>) mongoTemplate.findAll(Map.class, "domain_group");
        docs.forEach(doc -> {
            doc.put("id", doc.remove("_id").toString());
        });
        docs.sort(Comparator.comparing(o -> (String) o.getOrDefault("name", "")));
        return docs;
    }

    public Map<String, Object> createGroup(String name) {
        var existing = mongoTemplate.findOne(Query.query(Criteria.where("name").is(name)), Map.class, "domain_group");
        if (existing != null) throw new RuntimeException("Group already exists: " + name);

        Map<String, Object> doc = new HashMap<>();
        doc.put("name", name);
        doc.put("createdAt", new Date());
        doc.put("updatedAt", new Date());
        var saved = mongoTemplate.save(doc, "domain_group");
        saved.put("id", saved.remove("_id").toString());
        return saved;
    }

    public Map<String, Object> updateGroup(String groupId, String name) {
        var query = Query.query(Criteria.where("_id").is(groupId));
        var existing = mongoTemplate.findOne(query, Map.class, "domain_group");
        if (existing == null) throw new RuntimeException("Group not found");

        var dup = mongoTemplate.findOne(Query.query(Criteria.where("name").is(name)), Map.class, "domain_group");
        if (dup != null && !dup.get("_id").toString().equals(groupId))
            throw new RuntimeException("Group name already exists: " + name);

        existing.put("name", name);
        existing.put("updatedAt", new Date());
        mongoTemplate.save(existing, "domain_group");
        return existing;
    }

    public void deleteGroup(String groupId) {
        var query = Query.query(Criteria.where("_id").is(groupId));
        var existing = mongoTemplate.findOne(query, Map.class, "domain_group");
        if (existing == null) throw new RuntimeException("Group not found");

        // Remove groupId from related metas
        var updateQuery = Query.query(Criteria.where("groupId").is(groupId));
        mongoTemplate.updateMulti(updateQuery,
            org.springframework.data.mongodb.core.query.Update.update("groupId", null),
            "domain_meta");
        // Remove duplicates
        var unsetQuery = Query.query(Criteria.where("groupId").is(groupId));
        mongoTemplate.remove(unsetQuery, "domain_group");
    }

    // Domain meta CRUD
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listMeta() {
        List<Map<String, Object>> docs = (List<Map<String, Object>>) (List<?>) mongoTemplate.findAll(Map.class, "domain_meta");
        docs.forEach(d -> d.put("id", d.remove("_id").toString()));
        return docs;
    }

    public Map<String, Object> getMeta(String zoneId) {
        var doc = mongoTemplate.findOne(Query.query(Criteria.where("zoneId").is(zoneId)), Map.class, "domain_meta");
        if (doc == null) return null;
        doc.put("id", doc.remove("_id").toString());
        return doc;
    }

    public Map<String, Object> upsertMeta(Map<String, Object> body) {
        String zoneId = (String) body.get("zoneId");
        if (zoneId == null || zoneId.isBlank())
            throw new RuntimeException("zoneId is required");

        var query = Query.query(Criteria.where("zoneId").is(zoneId));
        var existing = mongoTemplate.findOne(query, Map.class, "domain_meta");
        if (existing == null) {
            existing = new HashMap<>();
            existing.put("zoneId", zoneId);
            existing.put("createdAt", new Date());
        }
        for (String field : Arrays.asList("type", "remark", "groupId", "name", "source")) {
            if (body.containsKey(field)) existing.put(field, body.get(field));
        }
        existing.put("updatedAt", new Date());
        mongoTemplate.save(existing, "domain_meta");
        existing.put("id", existing.remove("_id").toString());
        return existing;
    }

    public int batchUpsertMeta(List<Map<String, Object>> items) {
        int count = 0;
        for (var item : items) {
            upsertMeta(item);
            count++;
        }
        return count;
    }

    public void deleteMeta(String zoneId) {
        mongoTemplate.remove(Query.query(Criteria.where("zoneId").is(zoneId)), "domain_meta");
    }

}