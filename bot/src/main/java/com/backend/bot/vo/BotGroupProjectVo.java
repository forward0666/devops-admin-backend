package com.backend.bot.vo;

import com.backend.bot.entity.BotGroupProjectEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BotGroupProjectVo {
    private Long id;
    private String botName;
    private Long chatId;
    private String chatTitle;
    private Long projectId;
    private String projectName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BotGroupProjectVo fromEntity(BotGroupProjectEntity entity) {
        BotGroupProjectVo vo = new BotGroupProjectVo();
        vo.setId(entity.getId());
        vo.setBotName(entity.getBotName());
        vo.setChatId(entity.getChatId());
        vo.setChatTitle(entity.getChatTitle());
        vo.setProjectId(entity.getProjectId());
        vo.setProjectName(entity.getProjectName());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
