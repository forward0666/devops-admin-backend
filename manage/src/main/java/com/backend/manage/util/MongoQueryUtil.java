package com.backend.manage.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class MongoQueryUtil {

    /**
     * 根据 JSON 构建 MongoDB Query
     *
     * @param filterJson JSON 中的 filter
     * @param sortBy     排序字段
     * @param sortDir    排序方向
     * @param limit      限制数量（可为 null，不限制）
     * @return Query 对象
     */
    public static Query buildQueryFromJson(JsonNode filterJson, String sortBy, String sortDir, Integer limit) {
        Query query = new Query();

        // 构建 filter
        if (filterJson != null && filterJson.size() > 0) {
            Document filterDoc = Document.parse(filterJson.toString());
            filterDoc.forEach((key, value) -> query.addCriteria(Criteria.where(key).is(value)));
        }

        // 排序
        if (sortBy != null && sortDir != null) {
            Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
            query.with(Sort.by(direction, sortBy));
        }

        // 限制数量
        if (limit != null && limit > 0) {
            query.limit(limit);
        }

        return query;
    }
}
