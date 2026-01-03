# Repository 层移除计划

## 目标
完全移除 Repository 中间层，直接使用 MyBatis Mapper

## 涉及的文件

### Service 层（需要修改 - 4 个）
1. **DepartmentService** - 使用 DepartmentRepository
   - 修改：注入 DepartmentMapper 替代 DepartmentRepository
   - 操作：findAll → mapper.findAll()，findById → mapper.findById()
   
2. **UserService** - 使用 UserRepository  
   - 修改：注入 UserMapper 替代 UserRepository
   - 操作：similar mapping
   
3. **OperationLogService** - 使用 OperationLogRepository
   - 修改：注入 OperationLogMapper 替代 OperationLogRepository
   
4. **AuthService** - 使用 UserRepository
   - 修改：注入 UserMapper 替代 UserRepository

### Repository 接口（可以删除 - 4 个）
- `backend/manage/src/main/java/com/backend/manage/repository/system/DepartmentRepository.java`
- `backend/manage/src/main/java/com/backend/manage/repository/system/UserRepository.java`
- `backend/manage/src/main/java/com/backend/manage/repository/audits/OperationLogRepository.java`
- `backend/manage/src/main/java/com/backend/manage/repository/settings/SystemConfigRepository.java`

### RepositoryImpl 实现（可以删除 - 4 个）
- `backend/manage/src/main/java/com/backend/manage/repository/impl/system/DepartmentRepositoryImpl.java`
- `backend/manage/src/main/java/com/backend/manage/repository/impl/system/UserRepositoryImpl.java`
- `backend/manage/src/main/java/com/backend/manage/repository/impl/audits/OperationLogRepositoryImpl.java`
- `backend/manage/src/main/java/com/backend/manage/repository/impl/settings/SystemConfigRepositoryImpl.java`

## 修改步骤

### 步骤 1：修改 DepartmentService
- [ ] 移除 DepartmentRepository 导入
- [ ] 移除 DepartmentRepositoryImpl 导入  
- [ ] 添加 DepartmentMapper 导入
- [ ] 将 @Autowired private DepartmentRepository 改为 @Autowired private DepartmentMapper
- [ ] 替换所有 departmentRepository 调用为 departmentMapper

### 步骤 2：修改 UserService
- [ ] 移除 UserRepository 导入
- [ ] 添加 UserMapper 导入
- [ ] 替换注入
- [ ] 替换所有调用

### 步骤 3：修改 OperationLogService
- [ ] 移除 OperationLogRepository 导入
- [ ] 添加 OperationLogMapper 导入
- [ ] 替换注入
- [ ] 替换所有调用

### 步骤 4：修改 AuthService
- [ ] 移除 UserRepository 导入
- [ ] 添加 UserMapper 导入
- [ ] 替换注入
- [ ] 替换所有调用

### 步骤 5：删除 Repository 文件夹

### 步骤 6：验证编译
