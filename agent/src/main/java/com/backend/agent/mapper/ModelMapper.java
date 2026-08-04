package com.backend.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.backend.agent.entity.ModelEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelMapper extends BaseMapper<ModelEntity> {
}