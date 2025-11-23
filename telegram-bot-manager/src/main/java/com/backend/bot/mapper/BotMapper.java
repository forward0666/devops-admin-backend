// com.backend.bot.mapper.BotMapper.java
package com.backend.bot.mapper;

import com.backend.bot.entity.BotEntity;
//import org.apache.ibatis.annotations.Mapper;

//@Mapper
public interface BotMapper {


    int insert(BotEntity botEntity);

    BotEntity findByBotName(String botName);

    BotEntity findByToken(String token);
}