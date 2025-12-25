package com.backend.manage.model;

import java.time.LocalDateTime;

/**
 * 用户实体类 - 表示系统中的用户信息
 * 
 * 中文注释：这个类定义了用户的数据模型，包含用户的所有属性和相关操作方法
 * 用于在业务逻辑层和数据访问层之间传输用户数据
 * 对应数据库中的users表，包含用户基本信息、认证信息、状态信息等
 */
public class User {
    private Long id;                    // 用户ID，主键，自增长
    private String username;            // 用户名，唯一标识，用于登录
    private String password;            // 密码，加密存储
    private String email;               // 邮箱地址，用于通知和验证
    private String phone;               // 手机号码，用于短信验证和联系
    private String tgUsername;          // Telegram用户名，用于消息通知
    private String fullName;            // 用户全名，显示用
    private String avatarUrl;           // 头像URL，用户头像图片地址
    private Long departmentId;          // 部门ID，外键关联部门表
    private String position;            // 职位，如经理、工程师等
    private String employeeId;          // 员工编号，公司内部编号
    private String role;                // 角色，如admin、user等，用于权限控制
    private LocalDateTime createdAt;    // 创建时间，记录创建时间戳
    private LocalDateTime updatedAt;    // 更新时间，记录最后修改时间
    private Long createdBy;             // 创建者ID，记录创建此用户的用户ID
    private Long updatedBy;             // 更新者ID，记录最后修改此用户的用户ID
    private boolean active;             // 是否激活，false表示用户已被禁用
    private boolean emailVerified;      // 邮箱是否验证，true表示已验证
    private boolean phoneVerified;      // 手机是否验证，true表示已验证
    private LocalDateTime lastLoginAt;  // 最后登录时间，记录用户最后一次登录时间
    private String lastLoginIp;         // 最后登录IP，记录用户最后一次登录的IP地址
    private Integer loginCount;         // 登录次数，统计用户登录次数
    private LocalDateTime passwordChangedAt; // 密码修改时间，记录用户最后一次修改密码的时间

    // 构造方法
    /**
     * 默认无参构造方法 - 创建空的用户对象
     */
    public User() {}

    /**
     * 带参数构造方法 - 用于快速创建新用户对象
     * @param username 用户名
     * @param password 密码（未加密）
     * @param email 邮箱地址
     * @param fullName 用户全名
     * @param role 用户角色
     */
    public User(String username, String password, String email, String fullName, String role) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.active = true;             // 新用户默认激活
        this.emailVerified = false;     // 新用户邮箱未验证
        this.phoneVerified = false;     // 新用户手机未验证
        this.loginCount = 0;            // 新用户登录次数为0
    }

    // Getter和Setter方法
    /**
     * 获取用户ID
     * @return 用户ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置用户ID
     * @param id 用户ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取用户名
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取密码（加密后的）
     * @return 加密后的密码
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码（需要先加密）
     * @param password 加密后的密码
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取邮箱地址
     * @return 邮箱地址
     */
    public String getEmail() {
        return email;
    }

    /**
     * 设置邮箱地址
     * @param email 邮箱地址
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * 获取手机号码
     * @return 手机号码
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 设置手机号码
     * @param phone 手机号码
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * 获取Telegram用户名
     * @return Telegram用户名
     */
    public String getTgUsername() {
        return tgUsername;
    }

    /**
     * 设置Telegram用户名
     * @param tgUsername Telegram用户名
     */
    public void setTgUsername(String tgUsername) {
        this.tgUsername = tgUsername;
    }

    /**
     * 获取用户全名
     * @return 用户全名
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * 设置用户全名
     * @param fullName 用户全名
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * 获取头像URL
     * @return 头像图片URL地址
     */
    public String getAvatarUrl() {
        return avatarUrl;
    }

    /**
     * 设置头像URL
     * @param avatarUrl 头像图片URL地址
     */
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    /**
     * 获取部门ID
     * @return 所属部门ID
     */
    public Long getDepartmentId() {
        return departmentId;
    }

    /**
     * 设置部门ID
     * @param departmentId 所属部门ID
     */
    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    /**
     * 获取职位
     * @return 职位名称
     */
    public String getPosition() {
        return position;
    }

    /**
     * 设置职位
     * @param position 职位名称
     */
    public void setPosition(String position) {
        this.position = position;
    }

    /**
     * 获取员工编号
     * @return 员工编号
     */
    public String getEmployeeId() {
        return employeeId;
    }

    /**
     * 设置员工编号
     * @param employeeId 员工编号
     */
    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    /**
     * 获取用户角色
     * @return 角色名称，如admin、user等
     */
    public String getRole() {
        return role;
    }

    /**
     * 设置用户角色
     * @param role 角色名称
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * 获取创建时间
     * @return 创建时间戳
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间
     * @param createdAt 创建时间戳
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取更新时间
     * @return 最后更新时间戳
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间
     * @param updatedAt 最后更新时间戳
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 获取创建者ID
     * @return 创建此用户的用户ID
     */
    public Long getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置创建者ID
     * @param createdBy 创建此用户的用户ID
     */
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * 获取更新者ID
     * @return 最后更新此用户的用户ID
     */
    public Long getUpdatedBy() {
        return updatedBy;
    }

    /**
     * 设置更新者ID
     * @param updatedBy 最后更新此用户的用户ID
     */
    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    /**
     * 检查用户是否激活
     * @return true表示用户激活，false表示用户禁用
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 设置用户激活状态
     * @param active true激活，false禁用
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * 检查邮箱是否验证
     * @return true表示邮箱已验证，false表示未验证
     */
    public boolean isEmailVerified() {
        return emailVerified;
    }

    /**
     * 设置邮箱验证状态
     * @param emailVerified true已验证，false未验证
     */
    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    /**
     * 检查手机是否验证
     * @return true表示手机已验证，false表示未验证
     */
    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    /**
     * 设置手机验证状态
     * @param phoneVerified true已验证，false未验证
     */
    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    /**
     * 获取最后登录时间
     * @return 最后登录时间戳
     */
    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    /**
     * 设置最后登录时间
     * @param lastLoginAt 最后登录时间戳
     */
    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /**
     * 获取最后登录IP
     * @return 最后登录的IP地址
     */
    public String getLastLoginIp() {
        return lastLoginIp;
    }

    /**
     * 设置最后登录IP
     * @param lastLoginIp 最后登录的IP地址
     */
    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    /**
     * 获取登录次数
     * @return 总登录次数
     */
    public Integer getLoginCount() {
        return loginCount;
    }

    /**
     * 设置登录次数
     * @param loginCount 总登录次数
     */
    public void setLoginCount(Integer loginCount) {
        this.loginCount = loginCount;
    }

    /**
     * 获取密码修改时间
     * @return 最后一次修改密码的时间
     */
    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    /**
     * 设置密码修改时间
     * @param passwordChangedAt 最后一次修改密码的时间
     */
    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }
}
