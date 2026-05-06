package com.backend.bot.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("bot_menu")
public class BotMenuEntity {

    @Id
    private Long id;

    private String botName;

    private String botType;

    /** 1=主菜单, 2=子菜单 */
    private Integer menuLevel;

    /** 唯一标识，用于 callback_data 匹配 */
    private String menuKey;

    /** 显示标题 */
    private String title;

    /** 按钮JSON数组: [{"text":"xxx","callbackData":"xxx"}] */
    private String buttons;

    /** 上级菜单ID，主菜单为null */
    private Long parentId;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
