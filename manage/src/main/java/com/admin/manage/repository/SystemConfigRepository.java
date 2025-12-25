package com.admin.manage.repository;

import com.admin.manage.model.SystemConfig;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

/**
 * 系统配置数据访问层接口 - SystemConfig Repository
 * 
 * 提供系统配置的数据库操作接口，支持字符串、JSON对象等多种配置格式
 * 用于管理系统运行时配置，支持热更新配置信息
 * 
 * Repository interface for system configuration management
 */
public interface SystemConfigRepository {

    /**
     * 根据配置键获取配置值（字符串格式）
     * 如果配置不存在，返回默认值
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     * 
     * Get configuration value by key
     */
    String getConfigValue(String key, String defaultValue);

    /**
     * 设置配置值（字符串格式）
     * 用于更新或创建字符串类型的配置项
     * 
     * @param key 配置键
     * @param value 配置值
     * 
     * Set configuration value
     */
    void setConfigValue(String key, String value);

    /**
     * 获取配置值（JSON对象格式）
     * 将配置值反序列化为指定的Java对象类型
     * 
     * @param <T> 对象类型
     * @param key 配置键
     * @param clazz 目标对象类
     * @param defaultValue 默认对象
     * @return 反序列化后的对象或默认对象
     * 
     * Get configuration as JSON object
     */
    <T> T getConfigObject(String key, Class<T> clazz, T defaultValue);

    /**
     * 获取配置值（JSON对象格式，支持复杂类型）
     * 使用TypeReference支持List<String>等复杂泛型类型的反序列化
     * 
     * @param <T> 对象类型
     * @param key 配置键
     * @param typeRef 类型引用
     * @param defaultValue 默认对象
     * @return 反序列化后的对象或默认对象
     * 
     * Get configuration as JSON object with TypeReference (支持 List<String> 等复杂类型)
     */
    <T> T getConfigObject(String key, TypeReference<T> typeRef, T defaultValue);

    /**
     * 设置配置值（JSON对象格式）
     * 将Java对象序列化为JSON字符串后存储
     * 
     * @param key 配置键
     * @param value 要存储的Java对象
     * 
     * Set configuration as JSON object
     */
    void setConfigObject(String key, Object value);

    /**
     * 获取所有系统配置
     * 返回系统中所有配置项的完整列表
     * 
     * @return 所有系统配置的列表
     * 
     * Get all configurations
     */
    List<SystemConfig> getAllConfigs();

    /**
     * 获取公开配置（非敏感配置）
     * 返回可以公开访问的系统配置项，过滤掉敏感信息
     * 
     * @return 公开配置的列表
     * 
     * Get public configurations only
     */
    List<SystemConfig> getPublicConfigs();

    /**
     * 根据配置键获取配置对象
     * 返回完整的配置对象，包含配置键、值、描述等信息
     * 
     * @param key 配置键
     * @return 配置对象，如果不存在返回null
     * 
     * Get configuration by key
     */
    SystemConfig getConfigByKey(String key);

    /**
     * 创建新的配置项
     * 用于新增系统配置，需要提供完整的配置信息
     * 
     * @param config 配置对象
     * @return 创建后的配置对象
     * 
     * Create new configuration
     */
    SystemConfig createConfig(SystemConfig config);

    /**
     * 更新现有配置项
     * 用于修改已存在的系统配置信息
     * 
     * @param config 配置对象
     * @return 更新后的配置对象
     * 
     * Update existing configuration
     */
    SystemConfig updateConfig(SystemConfig config);

    /**
     * 根据配置键删除配置项
     * 删除指定的系统配置，通常用于清理不再需要的配置
     * 
     * @param key 配置键
     * @return true表示删除成功，false表示删除失败
     * 
     * Delete configuration by key
     */
    boolean deleteConfig(String key);

    /**
     * 检查配置项是否存在
     * 验证指定的配置键是否已在系统中存在
     * 
     * @param key 配置键
     * @return true表示配置存在，false表示不存在
     * 
     * Check if configuration exists
     */
    boolean configExists(String key);
}
