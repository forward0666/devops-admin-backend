package com.backend.manage.entity.system;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 系统配置实体类
 * 用于存储和管理各种系统配置参数
 *
 * 设计特点：
 * 1. 使用 @Table 注解映射到数据库表 setting
 * 2. 使用 @Id 注解标记主键
 * 3. 使用 Lombok @Data 注解自动生成 getter/setter
 * 4. 支持配置类型和公开/私有配置
 * 5. 包含审计字段
 *
 * @author Backend Team
 * @version 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("setting")
public class SettingEntity {
    /**
     * 主键 ID
     */
    @Id
    private Long id;

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置类型
     * - string: 字符串
     * - number: 数字
     * - boolean: 布尔值
     * - json: JSON 对象
     */
    private String configType;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 是否公开
     *
     * true: 公开配置，所有用户可见
     * false: 私有配置，仅管理员可见
     */
    private boolean isPublic;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建系统配置
     *
     * @param configKey 配置键
     * @param configValue 配置值
     * @param configType 配置类型
     * @param description 配置描述
     * @param isPublic 是否公开
     */
    public SettingEntity(String configKey, String configValue, String configType, String description, boolean isPublic) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.configType = configType;
        this.description = description;
        this.isPublic = isPublic;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
