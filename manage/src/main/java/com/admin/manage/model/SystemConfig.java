package com.admin.manage.model;

import java.time.LocalDateTime;

/**
 * 系统配置实体类 - 表示系统中的配置项信息
 * 
 * 中文注释：这个类定义了系统配置的数据模型，用于存储和管理各种系统配置参数
 * 对应数据库中的system_configs表，支持不同类型的配置项和访问控制
 * 配置项可以设置为公开或私有，用于前端显示或后端内部使用
 */
public class SystemConfig {
    private Long id;                    // 配置ID，主键，自增长
    private String configKey;           // 配置键，唯一标识，如"system.name"
    private String configValue;         // 配置值，存储具体的配置内容
    private String configType;          // 配置类型，如"string"、"number"、"boolean"等
    private String description;         // 配置描述，说明配置项的用途和含义
    private boolean isPublic;           // 是否公开，true表示前端可以访问，false表示仅后端使用
    private LocalDateTime createdAt;   // 创建时间，记录创建时间戳
    private LocalDateTime updatedAt;   // 更新时间，记录最后修改时间

    // 构造方法
    /**
     * 默认无参构造方法 - 创建空的配置对象
     */
    public SystemConfig() {}

    /**
     * 带参数构造方法 - 用于快速创建新配置对象
     * @param configKey 配置键，唯一标识
     * @param configValue 配置值
     * @param configType 配置类型
     * @param description 配置描述
     * @param isPublic 是否公开
     */
    public SystemConfig(String configKey, String configValue, String configType, String description, boolean isPublic) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.configType = configType;
        this.description = description;
        this.isPublic = isPublic;
    }

    // Getter和Setter方法
    /**
     * 获取配置ID
     * @return 配置ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置配置ID
     * @param id 配置ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取配置键
     * @return 配置键，唯一标识
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * 设置配置键
     * @param configKey 配置键，必须唯一
     */
    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    /**
     * 获取配置值
     * @return 配置值内容
     */
    public String getConfigValue() {
        return configValue;
    }

    /**
     * 设置配置值
     * @param configValue 配置值内容
     */
    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    /**
     * 获取配置类型
     * @return 配置类型，如"string"、"number"、"boolean"等
     */
    public String getConfigType() {
        return configType;
    }

    /**
     * 设置配置类型
     * @param configType 配置类型
     */
    public void setConfigType(String configType) {
        this.configType = configType;
    }

    /**
     * 获取配置描述
     * @return 配置项的描述信息
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置配置描述
     * @param description 配置项的描述信息
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 检查配置是否公开
     * @return true表示公开，前端可访问；false表示私有，仅后端使用
     */
    public boolean isPublic() {
        return isPublic;
    }

    /**
     * 设置配置公开状态
     * @param isPublic true公开，false私有
     */
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
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
}
