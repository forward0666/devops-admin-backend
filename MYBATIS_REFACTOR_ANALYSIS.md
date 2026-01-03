# MyBatis 全量重构分析报告

## 扫描结果

### 当前问题
backend/manage 服务目前混合使用 Spring Data JPA 和 MyBatis：
- **实体类**：使用 Spring Data JPA 注解（@Table, @Id）
- **数据访问**：使用 MyBatis Mapper 接口和 XML 配置
- **冗余性**：@Table 注解在 MyBatis 中不必要，因为 resultMap 已经处理了字段映射

### 涉及的实体类（6个）
1. **MenuEntity** - @Table("menus"), @Id 标记 menuId
2. **RoleEntity** - @Table("role"), @Id 标记 id
3. **PermissionMappingEntity** - @Table("permissions"), @Id 标记 roleId（复合主键）
4. **UserEntity** - @Table 注解
5. **DepartmentEntity** - @Table 注解
6. **PositionEntity** - @Table 注解

### 涉及的 Mapper（6个）
- MenuMapper.java / MenuMapper.xml
- RoleMapper.java / RoleMapper.xml
- PermissionMapper.java / PermissionMapper.xml
- UserMapper.java / UserMapper.xml
- DepartmentMapper.java / DepartmentMapper.xml
- PositionMapper.java / PositionMapper.xml

### 涉及的 Service 类（6个）
- MenuService
- RoleService
- PermissionService
- UserService
- DepartmentService
- PositionService

## 重构目标

### 阶段 1: 移除实体类注解
- 删除所有实体类中的 `@Table` 注解
- 删除所有实体类中的 `@Id` 注解
- 保留 Lombok 注解（@Data, @NoArgsConstructor, @AllArgsConstructor）
- **优势**：实体类变成纯 POJO，与 ORM 框架解耦

### 阶段 2: 验证 MyBatis 配置
- 检查 resultMap 定义是否完整
- 检查参数映射（parameterType）是否正确
- 检查 SQL 查询语句是否正确
- 特别关注复合主键的处理（PermissionMappingEntity）

### 阶段 3: 验证 Service 层
- 确保 Service 只依赖 MyBatis Mapper
- 移除任何对 Spring Data Repository 的引用
- 更新业务逻辑以适应 MyBatis 的使用方式

### 阶段 4: 编译和测试
- 运行 Maven 编译
- 执行单元测试
- 验证 API 功能

## 已识别的配置

### MyBatis 配置现状
```
✓ MenuMapper.xml - 包含 CRUD 操作和树形查询
✓ RoleMapper.xml - 包含角色查询
✓ PermissionMapper.xml - 包含权限映射查询（复合主键）
✓ UserMapper.xml - 包含用户查询
✓ DepartmentMapper.xml - 包含部门查询
✓ PositionMapper.xml - 包含职位查询
```

## 预期影响范围

### 需要修改的文件
```
entity/system/MenuEntity.java            (移除注解)
entity/system/RoleEntity.java            (移除注解)
entity/system/PermissionMappingEntity.java (移除注解)
entity/system/UserEntity.java            (移除注解)
entity/system/DepartmentEntity.java      (移除注解)
entity/system/PositionEntity.java        (移除注解)
```

### 需要验证的文件
```
mapper/system/MenuMapper.java
mapper/system/RoleMapper.java
mapper/system/PermissionMapper.java
mapper/system/UserMapper.java
mapper/system/DepartmentMapper.java
mapper/system/PositionMapper.java

mapper/MenuMapper.xml
mapper/RoleMapper.xml
mapper/PermissionMapper.xml
mapper/UserMapper.xml
mapper/DepartmentMapper.xml
mapper/PositionMapper.xml

service/MenuService.java
service/RoleService.java
service/PermissionService.java
service/UserService.java
service/DepartmentService.java
service/PositionService.java
```

## 重构步骤

1. **移除实体类注解**：6 个实体类
2. **验证 Mapper 接口**：确保参数名与 XML 查询匹配
3. **验证 XML resultMap**：确保字段映射完整
4. **检查 Service 逻辑**：移除 JPA 特定代码
5. **编译测试**：确保无编译错误
6. **功能测试**：验证 API 正常工作

## 预计工作量
- 实体类修改：15-20 分钟
- Mapper/XML 验证：30-45 分钟
- Service 检查：20-30 分钟
- 编译和测试：15-20 分钟
