package convert;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 通用数据类型转换工具类
 * 中文注释：提供对象类型安全转换、默认值处理、时间格式化等功能。
 *
 * 应用场景：
 * - 数据导入/导出时的类型安全转换
 * - Map → POJO 或 JSON 数据清洗
 * - 数据库或日志处理前的通用预处理
 */
public class ConvertUtils {

    /** 将对象转换为 List<Object>，非 List 返回空列表 */
    @SuppressWarnings("unchecked")
    public static List<Object> toList(Object val) {
        return val instanceof List ? (List<Object>) val : new ArrayList<>();
    }

    /** 将对象转换为 int，非数字返回 0 */
    public static int toInt(Object val) {
        return val instanceof Number ? ((Number) val).intValue() : 0;
    }

    /** 将对象转换为 boolean，支持 Boolean、String、Number 类型 */
    public static boolean toBool(Object val) {
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        if (val instanceof String) return Boolean.parseBoolean(((String) val).toLowerCase());
        return false;
    }

    /** 将对象转换为字符串，null 返回空字符串 */
    public static String toStr(Object val) {
        return val != null ? val.toString() : "";
    }

    /** 将布尔值或数字转换为 0/1 整型标识 */
    public static int toBoolInt(Object val) {
        if (val instanceof Boolean) return (Boolean) val ? 1 : 0;
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }

    /** 将对象转换为 double，非数字返回 0.0 */
    public static double toDouble(Object val) {
        return val instanceof Number ? ((Number) val).doubleValue() : 0.0;
    }

    /**
     * 将时间字符串转换为北京时间格式（yyyy-MM-dd HH:mm:ss）
     * 若解析失败，返回默认时间 "1970-01-01 00:00:00"
     */
    public static String toDateTime(Object value) {
        if (value == null) return "1970-01-01 00:00:00";
        try {
            OffsetDateTime odt = OffsetDateTime.parse(value.toString().trim());
            return odt.atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return "1970-01-01 00:00:00";
        }
    }

    /** 将对象转换为 long，非数字或格式错误返回 0L */
    public static long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return 0L;
        }
    }
}
