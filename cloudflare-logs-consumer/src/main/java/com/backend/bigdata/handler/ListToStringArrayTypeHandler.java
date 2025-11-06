package com.backend.bigdata.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * List到字符串数组类型处理器
 * 中文注释：MyBatis自定义类型处理器，用于处理List<Object>与数据库字符串数组之间的转换
 *
 * 功能说明：
 * 1. 将Java List<Object>转换为PostgreSQL兼容的数组字符串格式
 * 2. 将数据库中的数组字符串解析回Java List<Object>
 * 3. 支持字符串值的单引号转义处理
 *
 * 转换格式：
 * - Java端: List<String> ["value1", "value2", "value3"]
 * - 数据库端: ['value1','value2','value3'] (PostgreSQL数组格式)
 *
 * 使用场景：
 * - 处理Cloudflare日志中数组类型的字段存储
 * - 支持MyBatis映射复杂数据类型到数据库
 * - 用于PostgreSQL数组字段的读写操作
 *
 * 技术特性：
 * - 继承BaseTypeHandler实现自定义类型处理
 * - 支持PreparedStatement参数设置
 * - 支持ResultSet结果集解析
 * - 自动处理单引号转义和解析
 */
public class ListToStringArrayTypeHandler extends BaseTypeHandler<List<Object>> {
    public void setNonNullParameter(PreparedStatement ps, int i, List<Object> parameter, JdbcType jdbcType) throws SQLException {
        String arrayString = (String)parameter.stream().map(Object::toString).map((s) -> "'" + s.replace("'", "\\'") + "'").collect(Collectors.joining(",", "[", "]"));
        ps.setString(i, arrayString);
    }

    public List<Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return this.parseArray(rs.getString(columnName));
    }

    public List<Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return this.parseArray(rs.getString(columnIndex));
    }

    public List<Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return this.parseArray(cs.getString(columnIndex));
    }

    private List<Object> parseArray(String array) {
        return array != null && array.length() >= 2 ? (List)Arrays.stream(array.substring(1, array.length() - 1).split(",")).map((s) -> s.trim().replaceAll("^'|'$", "")).collect(Collectors.toList()) : List.of();
    }
}