package com.backend.user.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将对象转换为 JSON 字符串
     *
     * @param obj 待转换对象
     * @return JSON 字符串，转换失败时返回 obj.toString()
     */
    public static String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }
}
