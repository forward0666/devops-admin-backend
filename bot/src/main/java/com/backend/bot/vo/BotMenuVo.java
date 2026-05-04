package com.backend.bot.vo;

import com.backend.bot.entity.BotMenuEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BotMenuVo {
    private Long id;
    private String botName;
    private Integer menuLevel;
    private String menuKey;
    private String title;
    private String buttons;
    private Long parentId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BotMenuVo fromEntity(BotMenuEntity entity) {
        BotMenuVo vo = new BotMenuVo();
        vo.setId(entity.getId());
        vo.setBotName(entity.getBotName());
        vo.setMenuLevel(entity.getMenuLevel());
        vo.setMenuKey(entity.getMenuKey());
        vo.setTitle(entity.getTitle());
        vo.setButtons(entity.getButtons());
        vo.setParentId(entity.getParentId());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
