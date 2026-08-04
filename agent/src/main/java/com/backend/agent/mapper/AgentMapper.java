package com.backend.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.backend.agent.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}