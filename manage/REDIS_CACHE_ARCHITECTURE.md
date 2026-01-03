# Redis 缓存架构总结

## 整体流程：Service → Redis → Mapper → MySQL

### 架构流程图
```
┌─────────────────┐
│    Service      │  (DepartmentService, UserService, RoleService, PositionService, PermissionService)
└────────┬────────┘
         │
         ├─→ 检查 Redis 可用性
         │
         ├─→ 【缓存命中】从 CacheService 读取缓存
         │   └─→ 返回缓存数据
         │
         └─→ 【缓存未命中】
             │
             ├─→ 从 MyBatis Mapper 查询 MySQL 数据库
             │   └─→ DepartmentMapper.findAll() / UserMapper.findById() / etc.
             │
             ├─→ 将数据写入 Redis 缓存
             │   └─→ DepartmentCacheService.cacheDepartmentsList()
             │
             └─→ 返回数据给客户端
```

## 已实现的 Redis 缓存服务

### 1. 核心 CacheService
- **文件**: `com.backend.manage.service.CacheService`
- **功能**:
  - Redis 连接健康检查 (`isRedisAvailable()`)
  - 按前缀清理缓存 (`clearByPrefix()`)
  - Token 验证缓存

### 2. 领域特定缓存服务

#### DepartmentCacheService
```java
// 缓存操作
cacheDepartmentsList(List<DepartmentEntity>)       // 缓存部门列表
cacheDepartment(DepartmentEntity)                  // 缓存单个部门
getCachedDepartmentsList()                         // 获取部门列表缓存
getCachedDepartment(Long id)                       // 获取单个部门缓存
clearAllDepartmentCache()                          // 清除所有部门缓存
```

#### UserCacheService
```java
// 缓存操作
cacheUsersList(List<UserEntity>)
cacheUser(UserEntity)
getCachedUsersList()
getCachedUser(Long id)
clearAllUserCache()
```

#### RoleCacheService
```java
// 缓存操作
cacheRolesList(List<RoleEntity>)
cacheRole(RoleEntity)
getCachedRolesList()
getCachedRole(Long id)
clearAllRoleCache()
```

#### PositionCacheService
```java
// 缓存操作
cachePositionsList(List<PositionEntity>)
cachePosition(PositionEntity)
getCachedPositionsList()
getCachedPosition(Long id)
clearAllPositionCache()
```

#### PermissionCacheService
```java
// 缓存操作
cachePermissionMappingsByRole(Long roleId, List<PermissionMappingEntity>)
getCachedPermissionMappingsByRole(Long roleId)
clearPermissionMappingsByRoleCache(Long roleId)
clearAllMenuCache()  // 权限变化时清除菜单缓存
```

#### MenuCacheService
```java
// 缓存操作
cacheMenusList(List<MenuEntity>)
cacheMenu(MenuEntity)
getCachedMenusList()
getCachedMenu(Long id)
clearAllMenuCache()
```

#### SystemConfigCacheService
```java
// 缓存操作
cacheSystemSettings()
getCachedSystemSettings()
clearSystemSettingsCache(String key)
```

## Service 层缓存实现

### DepartmentService
```java
public List<DepartmentEntity> getAllDepartments() {
    // 1. 检查 Redis 可用性
    if (cacheService.isRedisAvailable()) {
        // 2. 尝试从缓存读取
        List<DepartmentEntity> cached = departmentCacheService.getCachedDepartmentsList();
        if (cached != null) return cached;
    }
    
    // 3. 缓存未命中，从数据库查询
    List<DepartmentEntity> departments = departmentMapper.findAll();
    
    // 4. 填充关联数据（用户信息）
    for (DepartmentEntity dept : departments) {
        populateDepartmentUsers(dept);
    }
    
    // 5. 写入 Redis 缓存
    if (cacheService.isRedisAvailable()) {
        departmentCacheService.cacheDepartmentsList(departments);
    }
    
    return departments;
}
```

### UserService
```java
@Transactional(readOnly = true)
public List<UserEntity> getAllUsers() {
    // 同样的缓存流程
    var cachedUsers = cacheService.isRedisAvailable() 
        ? userCacheService.getCachedUsersList() 
        : null;
    
    if (cachedUsers != null) return cachedUsers;
    
    var users = userMapper.findAll();
    
    if (cacheService.isRedisAvailable()) {
        userCacheService.cacheUsersList(users);
    }
    
    return users;
}
```

### RoleService
```java
public List<RoleEntity> getAllRoles() {
    if (cacheService.isRedisAvailable()) {
        var cachedRoles = roleCacheService.getCachedRolesList();
        if (cachedRoles != null) return cachedRoles;
    }
    
    var roles = roleMapper.findAll();
    
    if (cacheService.isRedisAvailable()) {
        roleCacheService.cacheRolesList(roles);
    }
    
    return roles;
}
```

### PositionService
```java
public List<PositionEntity> getAllPositions() {
    if (cacheService.isRedisAvailable()) {
        List<PositionEntity> cached = positionCacheService.getCachedPositionsList();
        if (cached != null) return cached;
    }
    
    List<PositionEntity> positions = positionMapper.findAll();
    
    if (cacheService.isRedisAvailable()) {
        positionCacheService.cachePositionsList(positions);
    }
    
    return positions;
}
```

### PermissionService
```java
public PermissionResponseDto getPermissionMappingByRoleId(Long roleId) {
    // 1. 从缓存读取权限映射
    if (cacheService.isRedisAvailable()) {
        var cached = cacheService.getCachedPermissionMappingsByRole(roleId);
        if (cached != null) {
            // 缓存命中逻辑...
        }
    }
    
    // 2. 缓存未命中，从数据库查询
    List<PermissionMappingEntity> mappings = permissionMapper.findByRoleIdWithMenus(roleId);
    
    // 3. 缓存结果
    if (cacheService.isRedisAvailable()) {
        cacheService.cachePermissionMappingsByRole(roleId, mappings);
    }
    
    return buildResponseDto(mappings);
}
```

### MenuService
```java
public List<MenuEntity> getAllMenus() {
    if (cacheService.isRedisAvailable()) {
        var cachedMenus = cacheService.getCachedMenusList();
        if (cachedMenus != null) return cachedMenus;
    }
    
    var menus = menuMapper.findAll();
    setParentNames(menus);  // 填充父菜单名称
    
    if (cacheService.isRedisAvailable()) {
        cacheService.cacheMenusList(menus);
    }
    
    return menus;
}
```

## 缓存清除策略

### 创建/更新/删除操作时清除缓存

```java
public DepartmentEntity createDepartment(DepartmentEntity department) {
    // ... 创建逻辑
    
    // 清除缓存，确保数据一致性
    if (cacheService.isRedisAvailable()) {
        departmentCacheService.clearAllDepartmentCache();
    }
    
    return createdDepartment;
}

public void updatePermissionMapping(Long roleId, List<Long> menuIds) {
    // ... 更新逻辑
    
    // 清除权限和菜单缓存
    if (cacheService.isRedisAvailable()) {
        cacheService.clearPermissionMappingsByRoleCache(roleId);
        cacheService.clearAllMenuCache();
    }
}
```

## Redis 配置

**文件**: `com.backend.manage.config.RedisConfig`

### RedisTemplate 配置
- Key 序列化: `StringRedisSerializer` (字符串)
- Value 序列化: `Jackson2JsonRedisSerializer` (JSON)
- 支持 Java 时间类型 (`JavaTimeModule`)

### CacheManager 配置
- 缓存过期时间: 可配置 (通常 30 分钟)
- 序列化方式: JSON
- 支持 TTL (Time To Live)

## 健康检查

```java
public boolean isRedisAvailable() {
    // 每 30 秒检查一次 Redis 连接
    if (redisAvailable && now - lastCheck < 30_000) return true;
    
    try {
        redisTemplate.getConnectionFactory().getConnection().ping();
        redisAvailable = true;
    } catch (Exception e) {
        redisAvailable = false;
        log.warn("Redis不可用: {}", e.getMessage());
    }
    
    return redisAvailable;
}
```

## 性能优势

| 操作 | 无缓存 | 有缓存 |
|------|------|------|
| 获取部门列表 | 查询数据库 (10-50ms) | 读 Redis (1-5ms) |
| 获取用户列表 | 查询数据库 (20-100ms) | 读 Redis (1-5ms) |
| 获取权限映射 | 联表查询 (50-200ms) | 读 Redis (1-5ms) |

**预期性能提升**: 10-100 倍

## 缓存键命名规范

```
menus:all              # 所有菜单列表
menu:{id}              # 单个菜单
departments:all        # 所有部门列表
department:{id}        # 单个部门
users:all              # 所有用户列表
user:{id}              # 单个用户
roles:all              # 所有角色列表
role:{id}              # 单个角色
positions:all          # 所有职位列表
position:{id}          # 单个职位
permissions:role:{roleId}  # 角色权限映射
```

## 故障处理

如果 Redis 不可用:
1. `cacheService.isRedisAvailable()` 返回 `false`
2. 服务绕过缓存，直接查询数据库
3. 日志记录 Redis 连接问题
4. 系统继续运行（降级模式）

## 缓存更新事件

权限变化时会触发级联清除:
```java
// 清除权限缓存
clearPermissionMappingsByRoleCache(roleId);

// 同时清除菜单缓存（因为权限可能影响可见菜单）
clearAllMenuCache();
```

---

**总结**: Redis 缓存已全面集成到所有主要 Service 中，采用 Cache-Aside 模式，保证数据一致性和性能优化。
