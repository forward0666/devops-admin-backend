package com.backend.domain.service;

import com.backend.utils.dto.ApiResponseDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * DNS Domain service - sync and query DNS records from Cloudflare MongoDB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DnsDomainService {

    private static final String COLLECTION = "domain";

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void initIndexes() {
        try {
            mongoTemplate.indexOps(COLLECTION).ensureIndex(new Index().on("name", org.springframework.data.domain.Sort.Direction.ASC));
            mongoTemplate.indexOps(COLLECTION).ensureIndex(new Index().on("type", org.springframework.data.domain.Sort.Direction.ASC));
            mongoTemplate.indexOps(COLLECTION).ensureIndex(new Index().on("account_id", org.springframework.data.domain.Sort.Direction.ASC));
            mongoTemplate.indexOps(COLLECTION).ensureIndex(new Index().on("zone_name", org.springframework.data.domain.Sort.Direction.ASC));
        } catch (Exception e) {
            log.warn("[DnsDomain] initIndexes failed (may already exist): {}", e.getMessage());
        }
    }

    public ApiResponseDto<Map<String, Object>> syncDnsDomains() {
        try {
            String manageDbName = mongoTemplate.getDb().getName();
            log.info("[DnsDomain] sync from cloudflare collections into {}", manageDbName);

            // Find all collections ending with _dns_records in the current DB
            Set<String> collectionNames = mongoTemplate.getCollectionNames();
            List<String> dnsCollections = new ArrayList<>();
            for (String name : collectionNames) {
                if (name.endsWith("_dns_records")) {
                    dnsCollections.add(name);
                }
            }

            if (dnsCollections.isEmpty()) {
                return ApiResponseDto.error("No dns_records collections found");
            }

            Date now = new Date();
            int totalSynced = 0;
            Set<String> seenRids = new HashSet<>();

            for (String collName : dnsCollections) {
                Query query = Query.query(Criteria.where("type").in("A", "CNAME"));
                query.fields()
                        .include("record_id", "zone_id", "zone_name", "account_id", "account_name",
                                "type", "name", "content", "proxied", "ttl", "priority");

                List<Map> records = mongoTemplate.find(query, Map.class, collName);
                if (records.isEmpty()) continue;

                log.info("[DnsDomain] {}: {} records", collName, records.size());
                for (Map record : records) {
                    String recordId = (String) record.get("record_id");
                    if (recordId == null || seenRids.contains(recordId)) continue;
                    seenRids.add(recordId);

                    Map<String, Object> syncFields = new HashMap<>();
                    syncFields.put("record_id", recordId);
                    syncFields.put("zone_id", record.get("zone_id"));
                    syncFields.put("zone_name", record.get("zone_name"));
                    syncFields.put("account_id", record.get("account_id"));
                    syncFields.put("account_name", record.get("account_name"));
                    syncFields.put("type", record.get("type"));
                    syncFields.put("name", record.get("name"));
                    syncFields.put("content", record.get("content"));
                    syncFields.put("proxied", record.getOrDefault("proxied", false));
                    syncFields.put("ttl", record.getOrDefault("ttl", 1));
                    syncFields.put("priority", record.get("priority"));
                    syncFields.put("synced_at", now);

                    Update update = new Update();
                    syncFields.forEach(update::set);

                    Query existsQuery = Query.query(Criteria.where("record_id").is(recordId));
                    Map existing = mongoTemplate.findOne(existsQuery, Map.class, COLLECTION);
                    if (existing == null) {
                        update.set("is_public", false);
                        update.set("is_ignored", false);
                        update.set("remark", "");
                        mongoTemplate.insert(syncFields, COLLECTION);
                        totalSynced++;
                    } else {
                        mongoTemplate.updateFirst(existsQuery, update, COLLECTION);
                        totalSynced++;
                    }
                }
            }

            // Remove stale records (where synced_at < now)
            Query staleQuery = Query.query(Criteria.where("synced_at").lt(now));
            long staleRemoved = mongoTemplate.remove(staleQuery, COLLECTION).getDeletedCount();
            log.info("[DnsDomain] stale removed: {}", staleRemoved);

            // Ensure default fields
            Update defaultsUpdate = new Update().set("is_public", false).set("is_ignored", false).set("remark", "");
            mongoTemplate.updateMulti(
                    Query.query(Criteria.where("is_public").exists(false)),
                    new Update().set("is_public", false), COLLECTION);
            mongoTemplate.updateMulti(
                    Query.query(Criteria.where("is_ignored").exists(false)),
                    new Update().set("is_ignored", false), COLLECTION);
            mongoTemplate.updateMulti(
                    Query.query(Criteria.where("remark").exists(false)),
                    new Update().set("remark", ""), COLLECTION);

            Map<String, Object> data = new HashMap<>();
            data.put("synced", totalSynced);
            data.put("collections", dnsCollections.size());
            log.info("[DnsDomain] sync complete: {} new records", totalSynced);
            return ApiResponseDto.success("ok", data);
        } catch (Exception e) {
            log.error("[DnsDomain] syncDnsDomains error: {}", e.getMessage(), e);
            return ApiResponseDto.error(e.getMessage());
        }
    }

    public ApiResponseDto<List<Map<String, Object>>> listDnsDomains(String keyword, String type, Boolean isIgnored) {
        try {
            Query query = new Query();
            List<Criteria> criteriaList = new ArrayList<>();
            if (type != null) {
                criteriaList.add(Criteria.where("type").is(type));
            }
            if (isIgnored != null) {
                criteriaList.add(Criteria.where("is_ignored").is(isIgnored));
            }
            if (keyword != null && !keyword.isEmpty()) {
                // Use regex for keyword search
                String regex = ".*" + keyword.replaceAll("[.^$*+?{}|\\\\]", "\\\\$0") + ".*";
                Criteria regexCriteria = new Criteria().orOperator(
                        Criteria.where("name").regex(regex, "i"),
                        Criteria.where("content").regex(regex, "i")
                );
                criteriaList.add(regexCriteria);
            }

            if (!criteriaList.isEmpty()) {
                query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
            }

            query.with(org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.ASC, "name"));
            List<Map> rows = mongoTemplate.find(query, Map.class, COLLECTION);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map row : rows) {
                row.put("id", row.get("_id").toString());
                row.remove("_id");
                result.add(row);
            }
            return ApiResponseDto.success("ok", result);
        } catch (Exception e) {
            log.error("[DnsDomain] listDnsDomains error: {}", e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        }
    }

    public ApiResponseDto<Map<String, Object>> toggleAllPublic(Map<String, Object> body) {
        try {
            boolean isPublic = Boolean.TRUE.equals(body.get("is_public"));
            var result = mongoTemplate.updateMulti(
                    new Query(),
                    Update.update("is_public", isPublic),
                    COLLECTION);
            Map<String, Object> data = new HashMap<>();
            data.put("updated", result.getModifiedCount());
            return ApiResponseDto.success("ok", data);
        } catch (Exception e) {
            log.error("[DnsDomain] toggleAllPublic error: {}", e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        }
    }

    public ApiResponseDto<Void> updateDnsDomain(String recordId, Map<String, Object> body) {
        try {
            ObjectId oid;
            try {
                oid = new ObjectId(recordId);
            } catch (Exception e) {
                return ApiResponseDto.error("Invalid record ID");
            }

            Update update = new Update();
            if (body.containsKey("is_public")) update.set("is_public", body.get("is_public"));
            if (body.containsKey("is_ignored")) update.set("is_ignored", body.get("is_ignored"));
            if (body.containsKey("remark")) update.set("remark", body.get("remark"));

            if (update.getUpdateObject().isEmpty()) {
                return ApiResponseDto.error("No fields to update");
            }

            Query query = Query.query(Criteria.where("_id").is(oid));
            Map existing = mongoTemplate.findOne(query, Map.class, COLLECTION);
            if (existing == null) {
                return ApiResponseDto.error("Record not found");
            }

            mongoTemplate.updateFirst(query, update, COLLECTION);
            return ApiResponseDto.success("ok", null);
        } catch (Exception e) {
            log.error("[DnsDomain] updateDnsDomain error: {}", e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        }
    }
}