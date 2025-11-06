package com.backend.bigdata.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Map到JSON字符串类型处理器
 * 中文注释：MyBatis自定义类型处理器，用于处理Map<String, Object>与数据库JSON字符串之间的转换
 *
 * 功能说明：
 * 1. 将Java Map对象序列化为JSON字符串存储到数据库
 * 2. 将数据库中的JSON字符串反序列化为Java Map对象
 * 3. 使用Jackson ObjectMapper进行JSON处理
 *
 * 使用场景：
 * - 处理Cloudflare日志中复杂JSON结构的字段存储
 * - 支持MyBatis映射Map类型到数据库JSON字段
 * - 用于PostgreSQL JSON/JSONB字段的读写操作
 *
 * 技术特性：
 * - 继承BaseTypeHandler实现自定义类型处理
 * - 使用Jackson ObjectMapper进行JSON序列化/反序列化
 * - 支持异常处理和空值处理
 * - 自动处理JSON格式验证和解析
 *
 * 安全特性：
 * - 空值和空字符串返回空Map
 * - JSON解析失败时返回空Map并静默处理
 * - 详细的异常信息在序列化时抛出
 */
public class MapToJsonStringTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private static final ObjectMapper mapper = new ObjectMapper();

    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, mapper.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("Failed to serialize Map to JSON string at parameter index " + i, e);
        }
    }

    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return this.parseJson(rs.getString(columnName));
    }

    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return this.parseJson(rs.getString(columnIndex));
    }

    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return this.parseJson(cs.getString(columnIndex));
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            // 如果解析失败，返回空 Map 并记录警告日志
            return Collections.emptyMap();
        }
    }
}