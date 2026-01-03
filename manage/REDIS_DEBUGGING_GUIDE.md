# Redis 缓存调试指南

## 问题诊断流程

### 1️⃣ 检查 Redis 连接

#### 查看日志中的 Redis 连接状态

```
✅ Redis 连接正常     → Redis 连接成功
❌ Redis 不可用: ...  → Redis 连接失败
⚠️  Redis 不可用，无法... → 缓存操作被跳过
```

**日志位置**：应用启动时和每个请求时都会输出

### 2️⃣ 追踪缓存写入过程

#### 日志输出顺序（成功情况）

```log
[DepartmentService]     正在获取所有部门列表
[DepartmentService]     ✅ Redis 可用，尝试读取缓存
[DepartmentService]     🔍 从数据库查询部门列表
[DepartmentMapper]      SELECT * FROM departments
[DepartmentService]     📝 将 5 个部门缓存到 Redis
[DepartmentCacheService] ✅ 部门列表已缓存到 Redis: departments:list
[DepartmentService]     成功获取 5 个部门
```

#### 日志输出顺序（缓存命中情况）

```log
[DepartmentService]     正在获取所有部门列表
[DepartmentService]     ✅ Redis 可用，尝试读取缓存
[DepartmentCacheService] ✅ 从 Redis 读取部门列表缓存成功: departments:list
[DepartmentService]     ✅ 从缓存中获取部门列表成功，共 5 个部门
```

### 3️⃣ Redis 客户端验证

#### 使用 redis-cli 检查 key

```bash
# 连接 Redis
redis-cli

# 查看所有 key
KEYS *

# 查看特定 key
GET departments:list
GET department:1

# 查看 key 的过期时间
TTL departments:list

# 查看 key 的类型
TYPE departments:list

# 查看 Redis 内存使用
INFO memory

# 清空所有缓存（调试用）
FLUSHALL
```

### 4️⃣ 可能的问题及解决方案

#### 问题 A: Redis 连接失败

**症状**：
```
❌ Redis 不可用: Connection refused
```

**原因**：
- Redis 服务未启动
- Redis 连接地址/端口错误
- 防火墙阻止

**解决**：
```bash
# 启动 Redis
redis-server

# 验证 Redis 运行
redis-cli ping
# 应该返回 PONG
```

#### 问题 B: 数据写入成功但 key 不存在

**症状**：
```
✅ 部门列表已缓存到 Redis: departments:list
🔍 Redis 中找不到部门列表缓存: departments:list
```

**原因**：
- RedisTemplate 的序列化器未正确配置
- 数据大小超过 Redis 限制
- TTL 过短，key 已过期

**解决**：
```java
// 检查 RedisConfig.java 的序列化配置
@Bean
public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    
    // ✅ 必须配置序列化器
    StringRedisSerializer stringSerializer = new StringRedisSerializer();
    Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = 
        new Jackson2JsonRedisSerializer<>(Object.class);
    
    template.setKeySerializer(stringSerializer);
    template.setValueSerializer(jackson2JsonRedisSerializer);
    // ... 其他配置
    
    return template;
}
```

#### 问题 C: RedisTemplate 注入失败

**症状**：
```
NoSuchBeanDefinitionException: No qualifying bean of type 'RedisTemplate'
```

**原因**：
- RedisConfig 未被扫描
- Spring 未找到 RedisConnectionFactory

**解决**：
1. 检查 RedisConfig 所在包是否被 ComponentScan 扫描
2. 检查 pom.xml 是否添加了 `spring-boot-starter-data-redis`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

#### 问题 D: 缓存写入但读取返回 null

**症状**：
```
✅ 部门列表已缓存到 Redis: departments:list
🔍 Redis 中找不到部门列表缓存: departments:list
```

**原因**：
- 类型转换失败（instanceof 检查失败）
- 反序列化异常

**解决**：
```java
public List<DepartmentEntity> getCachedDepartmentsList() {
    Object v = redisTemplate.opsForValue().get(DEPT_LIST);
    
    // 添加调试信息
    if (v != null) {
        log.debug("🔍 缓存值类型: {}", v.getClass().getName());
        log.debug("🔍 缓存值内容: {}", v);
    }
    
    return (v instanceof List<?>) ? (List<DepartmentEntity>) v : null;
}
```

### 5️⃣ 性能指标

#### 正常情况下的性能

| 操作 | 预期耗时 | 日志特征 |
|------|--------|--------|
| 首次查询（写入缓存） | 50-200ms | 包含数据库查询和缓存写入日志 |
| 后续查询（读取缓存） | 1-5ms | 只包含缓存读取日志，无数据库查询 |

#### 检查点

```log
# ✅ 正常的缓存命中
[Service]  正在获取所有部门列表        (时间 T1)
[Service]  ✅ Redis 可用
[Service]  ✅ 从缓存中获取成功           (时间 T2, 耗时 = T2-T1 应该 < 5ms)

# ❌ 异常的缓存未命中
[Service]  正在获取所有部门列表        (时间 T1)
[Service]  ✅ Redis 可用
[Service]  🔍 从数据库查询部门列表
[Mapper]   SELECT * FROM departments   (数据库查询)
[Service]  📝 将 N 个部门缓存到 Redis   (时间 T2, 耗时 = T2-T1 应该 > 50ms)
```

## 完整诊断命令

### 1. 查看应用日志

```bash
# 关键词过滤
grep "✅\|❌\|🔍\|📝" application.log

# 查看 Redis 相关日志
grep -E "Redis|cache|CACHE" application.log
```

### 2. 检查 Redis 数据

```bash
redis-cli
KEYS *                           # 查看所有 key
KEYS "department*"               # 查看部门相关 key
GET departments:list             # 查看部门列表缓存
STRLEN departments:list          # 查看缓存大小（字节）
TTL departments:list             # 查看剩余过期时间
DBSIZE                          # 查看 Redis 中的 key 总数
```

### 3. 测试缓存流程

```bash
# 第一次请求（应该查询数据库）
curl http://localhost:8083/api/departments

# 查看日志应该包含:
# - "从数据库查询部门列表"
# - "部门列表已缓存到 Redis"

# 检查 Redis key
redis-cli KEYS "departments:list"

# 第二次请求（应该读取缓存）
curl http://localhost:8083/api/departments

# 查看日志应该包含:
# - "从 Redis 读取部门列表缓存成功"
# - 不应该包含数据库查询日志
```

## 常见配置问题

### application.yml 配置检查

```yaml
spring:
  redis:
    host: localhost          # ✅ 确保连接地址正确
    port: 6379              # ✅ 确保端口正确
    password: ''            # ✅ 如果有密码需要配置
    timeout: 2000ms
    database: 0
```

### RedisConfig 配置检查

```java
@Configuration
@EnableCaching  // ✅ 必须启用缓存
public class RedisConfig {
    
    @Bean("redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // ✅ 必须正确配置序列化
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = 
            new Jackson2JsonRedisSerializer<>(Object.class);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        mapper.registerModule(new JavaTimeModule());
        jackson2JsonRedisSerializer.setObjectMapper(mapper);
        
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
```

## 调试命令总结

```bash
# 启动 Redis
redis-server

# 连接 Redis CLI
redis-cli

# 查看缓存
KEYS "departments:list"
GET departments:list"

# 清空缓存（测试用）
FLUSHDB

# 监控 Redis 命令（实时看缓存操作）
MONITOR

# 查看 Redis 统计信息
INFO
```

## 下一步

如果按上述步骤检查后仍未生成 key：

1. **查看完整日志输出**，确认是否有错误堆栈
2. **检查 RedisTemplate 是否被正确注入**
3. **验证序列化器配置**
4. **查看 Redis 是否真的在运行**

---

**更新日期**: 2026-01-03  
**添加功能**: 详细日志输出 + 调试指南
