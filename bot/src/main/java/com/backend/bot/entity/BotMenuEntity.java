package com.backend.bot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("bot_menu")
public class BotMenuEntity {

    @Id
    private Long id;

    private String botType;

    private Integer menuLevel;

    private String menuKey;

    private String title;

    private String buttons;

    private Long parentId;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
