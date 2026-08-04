package com.backend.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.backend.domain.entity.StatisticEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StatisticMapper extends BaseMapper<StatisticEntity> {
}