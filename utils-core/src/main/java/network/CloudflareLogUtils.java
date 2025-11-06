package network;
import java.util.*;

import static convert.ConvertUtils.*;

/**
 * Cloudflare 日志工具类
 * 中文注释：提供 Cloudflare 日志数据的处理与转换功能，包括：
 *  - 扁平化 Map 结构（将嵌套字段展开为单层结构，便于数据库插入）
 *  - 各类数据类型安全转换（int、boolean、double、字符串、时间）
 *  - 空值与格式异常的容错处理
 *
 * 设计目的：
 *  - 将复杂嵌套的 Cloudflare 日志 JSON 数据结构转换为 ClickHouse / PostgreSQL 可直接存储的平面结构；
 *  - 避免数据库层出现类型不匹配；
 *  - 在 ETL（Extract-Transform-Load）过程中提高数据一致性与鲁棒性。
 *
 * 使用场景：
 *  - Cloudflare 日志采集 → Kafka → Flink / Java Consumer → 扁平化转换 → ClickHouse 存储
 *  - 后端 API 日志分析
 *  - 测试环境日志清洗
 *
 * 技术特点：
 *  - 静态方法纯函数设计，线程安全；
 *  - 各类型转换函数具备容错性；
 *  - 所有字段名称与 Cloudflare LogFields 对齐；
 *  - 可扩展性：新增字段时仅需在 flattenAndBuildParams() 中添加映射。
 */
public class CloudflareLogUtils {

    /**
     * 扁平化日志 Map 并构建参数映射
     * 中文注释：将原始 Cloudflare 日志 Map 扁平化，转换为数据库可用结构。
     *
     * 处理逻辑：
     * 1. 遍历 Cloudflare 日志的多层嵌套字段；
     * 2. 使用辅助方法（toInt、toStr、toBool、toDateTime等）将原始数据转换为目标类型；
     * 3. 对于 Map 类型字段（如 Cookies / RequestHeaders / JA4Signals），使用空 Map 兜底；
     * 4. 对于 List 类型字段（如 BotTags / SecuritySources），统一为空 List；
     *
     * @param original 原始 Cloudflare 日志 Map
     * @return 扁平化后的参数 Map，可直接插入数据库
     */
    public static Map<String, Object> flattenAndBuildParams(Map<String, Object> original) {
        Map<String, Object> result = new HashMap<>();

        // ==================== 1️⃣ Bot 相关字段 ====================
        result.put("BotDetectionIDs", toList(original.get("BotDetectionIDs")));
        result.put("BotDetectionTags", toList(original.get("BotDetectionTags")));
        result.put("BotScore", toInt(original.get("BotScore")));
        result.put("BotScoreSrc", toStr(original.get("BotScoreSrc")));
        result.put("BotTags", toList(original.get("BotTags")));

        // ==================== 2️⃣ Cache 缓存相关 ====================
        result.put("CacheCacheStatus", toStr(original.get("CacheCacheStatus")));
        result.put("CacheReserveUsed", toBool(original.get("CacheReserveUsed")));
        result.put("CacheResponseBytes", toInt(original.get("CacheResponseBytes")));
        result.put("CacheResponseStatus", toInt(original.get("CacheResponseStatus")));
        result.put("CacheTieredFill", toBool(original.get("CacheTieredFill")));

        // ==================== 3️⃣ Client 客户端信息 ====================
        result.put("ClientASN", toInt(original.get("ClientASN")));
        result.put("ClientCity", toStr(original.get("ClientCity")));
        result.put("ClientCountry", toStr(original.get("ClientCountry")));
        result.put("ClientDeviceType", toStr(original.get("ClientDeviceType")));
        result.put("ClientIP", toStr(original.get("ClientIP")));
        result.put("ClientIPClass", toStr(original.get("ClientIPClass")));
        result.put("ClientLatitude", toStr(original.get("ClientLatitude")));
        result.put("ClientLongitude", toStr(original.get("ClientLongitude")));
        result.put("ClientMTLSAuthCertFingerprint", toStr(original.get("ClientMTLSAuthCertFingerprint")));
        result.put("ClientMTLSAuthStatus", toStr(original.get("ClientMTLSAuthStatus")));
        result.put("ClientRegionCode", toStr(original.get("ClientRegionCode")));
        result.put("ClientRequestBytes", toInt(original.get("ClientRequestBytes")));
        result.put("ClientRequestHost", toStr(original.get("ClientRequestHost")));
        result.put("ClientRequestMethod", toStr(original.get("ClientRequestMethod")));
        result.put("ClientRequestPath", toStr(original.get("ClientRequestPath")));
        result.put("ClientRequestProtocol", toStr(original.get("ClientRequestProtocol")));
        result.put("ClientRequestReferer", toStr(original.get("ClientRequestReferer")));
        result.put("ClientRequestScheme", toStr(original.get("ClientRequestScheme")));
        result.put("ClientRequestSource", toStr(original.get("ClientRequestSource")));
        result.put("ClientRequestURI", toStr(original.get("ClientRequestURI")));
        result.put("ClientRequestUserAgent", toStr(original.get("ClientRequestUserAgent")));
        result.put("ClientSSLCipher", toStr(original.get("ClientSSLCipher")));
        result.put("ClientSSLProtocol", toStr(original.get("ClientSSLProtocol")));
        result.put("ClientSrcPort", toInt(original.get("ClientSrcPort")));
        result.put("ClientTCPRTTMs", toInt(original.get("ClientTCPRTTMs")));
        result.put("ClientXRequestedWith", toStr(original.get("ClientXRequestedWith")));

        // ==================== 4️⃣ 内容扫描 ====================
        result.put("ContentScanObjResults", toList(original.get("ContentScanObjResults")));
        result.put("ContentScanObjSizes", toList(original.get("ContentScanObjSizes")));
        result.put("ContentScanObjTypes", toList(original.get("ContentScanObjTypes")));

        // Cookies: 如果不是 Map 类型，则用空 Map 替代，防止空指针异常
        Object cookies = original.get("Cookies");
        result.put("Cookies", cookies instanceof Map ? cookies : new HashMap<>());

        // ==================== 5️⃣ Edge 边缘节点 ====================
        result.put("EdgeCFConnectingO2O", toBoolInt(original.get("EdgeCFConnectingO2O")));
        result.put("EdgeColoCode", toStr(original.get("EdgeColoCode")));
        result.put("EdgeColoID", toInt(original.get("EdgeColoID")));
        result.put("EdgeEndTimestamp", toDateTime(original.get("EdgeEndTimestamp")));
        result.put("EdgePathingOp", toStr(original.get("EdgePathingOp")));
        result.put("EdgePathingSrc", toStr(original.get("EdgePathingSrc")));
        result.put("EdgePathingStatus", toStr(original.get("EdgePathingStatus")));
        result.put("EdgeRequestHost", toStr(original.get("EdgeRequestHost")));
        result.put("EdgeResponseBodyBytes", toInt(original.get("EdgeResponseBodyBytes")));
        result.put("EdgeResponseBytes", toInt(original.get("EdgeResponseBytes")));
        result.put("EdgeResponseCompressionRatio", toDouble(original.get("EdgeResponseCompressionRatio")));
        result.put("EdgeResponseContentType", toStr(original.get("EdgeResponseContentType")));
        result.put("EdgeResponseStatus", toInt(original.get("EdgeResponseStatus")));
        result.put("EdgeServerIP", toStr(original.get("EdgeServerIP")));
        result.put("EdgeStartTimestamp", toDateTime(original.get("EdgeStartTimestamp")));
        result.put("EdgeTimeToFirstByteMs", toInt(original.get("EdgeTimeToFirstByteMs")));

        // ==================== 6️⃣ JA3 / JA4 指纹 ====================
        result.put("JA3Hash", toStr(original.get("JA3Hash")));
        result.put("JA4", toStr(original.get("JA4")));

        Object ja4Signals = original.get("JA4Signals");
        result.put("JA4Signals", ja4Signals instanceof Map ? ja4Signals : new HashMap<>());

        result.put("LeakedCredentialCheckResult", toStr(original.get("LeakedCredentialCheckResult")));

        // ==================== 7️⃣ Origin 源站信息 ====================
        result.put("OriginDNSResponseTimeMs", toInt(original.get("OriginDNSResponseTimeMs")));
        result.put("OriginIP", toStr(original.get("OriginIP")));
        result.put("OriginRequestHeaderSendDurationMs", toInt(original.get("OriginRequestHeaderSendDurationMs")));
        result.put("OriginResponseBytes", toInt(original.get("OriginResponseBytes")));
        result.put("OriginResponseDurationMs", toInt(original.get("OriginResponseDurationMs")));
        result.put("OriginResponseHTTPExpires", toStr(original.get("OriginResponseHTTPExpires")));
        result.put("OriginResponseHTTPLastModified", toStr(original.get("OriginResponseHTTPLastModified")));
        result.put("OriginResponseHeaderReceiveDurationMs", toInt(original.get("OriginResponseHeaderReceiveDurationMs")));
        result.put("OriginResponseStatus", toInt(original.get("OriginResponseStatus")));
        result.put("OriginResponseTime", toLong(original.get("OriginResponseTime")));
        result.put("OriginSSLProtocol", toStr(original.get("OriginSSLProtocol")));
        result.put("OriginTCPHandshakeDurationMs", toInt(original.get("OriginTCPHandshakeDurationMs")));
        result.put("OriginTLSHandshakeDurationMs", toInt(original.get("OriginTLSHandshakeDurationMs")));

        // ==================== 8️⃣ Ray ID 与头部信息 ====================
        result.put("ParentRayID", toStr(original.get("ParentRayID")));
        result.put("RayID", toStr(original.get("RayID")));

        Object requestHeaders = original.get("RequestHeaders");
        result.put("RequestHeaders", requestHeaders instanceof Map ? requestHeaders : new HashMap<>());

        Object responseHeaders = original.get("ResponseHeaders");
        result.put("ResponseHeaders", responseHeaders instanceof Map ? responseHeaders : new HashMap<>());

        // ==================== 9️⃣ Security 安全策略 ====================
        result.put("SecurityAction", toStr(original.get("SecurityAction")));
        result.put("SecurityActions", toList(original.get("SecurityActions")));
        result.put("SecurityRuleDescription", toStr(original.get("SecurityRuleDescription")));
        result.put("SecurityRuleID", toStr(original.get("SecurityRuleID")));
        result.put("SecurityRuleIDs", toList(original.get("SecurityRuleIDs")));
        result.put("SecuritySources", toList(original.get("SecuritySources")));

        // ==================== 🔟 SmartRoute / Tier 智能路由 ====================
        result.put("SmartRouteColoID", toInt(original.get("SmartRouteColoID")));
        result.put("UpperTierColoID", toInt(original.get("UpperTierColoID")));

        // ==================== 11️⃣ WAF 攻击检测 ====================
        result.put("WAFAttackScore", toInt(original.get("WAFAttackScore")));
        result.put("WAFFlags", toStr(original.get("WAFFlags")));
        result.put("WAFMatchedVar", toStr(original.get("WAFMatchedVar")));
        result.put("WAFRCEAttackScore", toInt(original.get("WAFRCEAttackScore")));
        result.put("WAFSQLiAttackScore", toInt(original.get("WAFSQLiAttackScore")));
        result.put("WAFXSSAttackScore", toInt(original.get("WAFXSSAttackScore")));

        // ==================== 12️⃣ Worker 脚本 ====================
        result.put("WorkerCPUTime", toInt(original.get("WorkerCPUTime")));
        result.put("WorkerScriptName", toStr(original.get("WorkerScriptName")));
        result.put("WorkerStatus", toStr(original.get("WorkerStatus")));
        result.put("WorkerSubrequest", toBoolInt(original.get("WorkerSubrequest")));
        result.put("WorkerSubrequestCount", toInt(original.get("WorkerSubrequestCount")));
        result.put("WorkerWallTimeUs", toInt(original.get("WorkerWallTimeUs")));

        // ==================== 13️⃣ Zone 域名信息 ====================
        result.put("ZoneName", toStr(original.get("ZoneName")));

        return result;
    }

}
