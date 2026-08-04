package com.backend.cloudflare.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Updates;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MongoDB 服务 - 封装对 cloudflare 和 domain 库的操作
 */
@Slf4j
@Service
public class MongoDbService {

    @Autowired
    @Qualifier("cloudflareMongoTemplate")
    private MongoTemplate cfMongoTemplate;

    @Autowired(required = false)
    @Qualifier("domainMongoTemplate")
    private MongoTemplate domainMongoTemplate;

    public MongoDatabase getCfDatabase() {
        return cfMongoTemplate.getDb();
    }

    public MongoDatabase getDomainDatabase() {
        if (domainMongoTemplate != null) {
            return domainMongoTemplate.getDb();
        }
        return cfMongoTemplate.getDb();
    }

    public MongoCollection<Document> getCollection(String collectionName) {
        return getCfDatabase().getCollection(collectionName);
    }

    public MongoCollection<Document> getDomainCollection(String collectionName) {
        return getDomainDatabase().getCollection(collectionName);
    }

    /**
     * 获取 account_{accountId}_{suffix} 集合
     */
    public String getCollectionName(Long accountId, String suffix) {
        return "account_" + accountId + "_" + suffix;
    }

    /**
     * 获取 account_{accountId}_zone_{zoneId}_{suffix} 集合
     */
    public String getZoneCollectionName(Long accountId, String zoneId, String suffix) {
        return "account_" + accountId + "_zone_" + zoneId + "_" + suffix;
    }

    /**
     * 列出所有集合名称
     */
    public Set<String> listCollectionNames() {
        Set<String> names = new java.util.LinkedHashSet<>();
        getCfDatabase().listCollectionNames().into(new ArrayList<>()).forEach(names::add);
        return names;
    }

    public Set<String> listDomainCollectionNames() {
        Set<String> names = new java.util.LinkedHashSet<>();
        getDomainDatabase().listCollectionNames().into(new ArrayList<>()).forEach(names::add);
        return names;
    }

    /**
     * 查找匹配前缀的集合
     */
    public List<String> findCollectionsByPrefix(String prefix) {
        List<String> result = new ArrayList<>();
        for (String name : listCollectionNames()) {
            if (name.startsWith(prefix)) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * 创建唯一索引
     */
    public void createUniqueIndex(String collectionName, String... fields) {
        MongoCollection<Document> coll = getCollection(collectionName);
        List<Document> indexKeys = new ArrayList<>();
        for (String field : fields) {
            indexKeys.add(new Document(field, 1));
        }
        Document index = new Document();
        for (Document key : indexKeys) {
            index.putAll(key);
        }
        coll.createIndex(index, new IndexOptions().unique(true));
    }

    /**
     * 创建普通索引
     */
    public void createIndex(String collectionName, String field) {
        getCollection(collectionName).createIndex(new Document(field, 1));
    }

    /**
     * 插入或更新文档（upsert）
     */
    public void upsert(String collectionName, Bson filter, Document doc) {
        MongoCollection<Document> coll = getCollection(collectionName);
        Document existing = coll.find(filter).first();
        if (existing != null) {
            coll.updateOne(filter, new Document("$set", doc));
        } else {
            coll.insertOne(doc);
        }
    }

    /**
     * 获取 zone 集合中所有 zone
     */
    public List<Document> getAllZones(Long accountId) {
        String collName = getCollectionName(accountId, "zones");
        List<Document> zones = new ArrayList<>();
        getCollection(collName).find().into(zones);
        return zones;
    }

    /**
     * 从所有 account 的 zone 集合获取 zone
     */
    public List<Document> getAllZonesFromAllAccounts() {
        List<Document> allZones = new ArrayList<>();
        for (String colName : listCollectionNames()) {
            if (colName.endsWith("_zones")) {
                getCollection(colName).find().into(allZones);
            }
        }
        return allZones;
    }

    /**
     * 根据 zoneId 列表和 account 列表获取 zones
     */
    public List<Document> getZonesByAccountIds(List<Long> accountIds) {
        List<Document> allZones = new ArrayList<>();
        for (Long accId : accountIds) {
            String collName = getCollectionName(accId, "zones");
            getCollection(collName).find().into(allZones);
        }
        return allZones;
    }
}