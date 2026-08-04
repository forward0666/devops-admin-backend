package com.backend.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.backend.monitor.entity.MonitorEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MonitorMapper extends BaseMapper<MonitorEntity> {
}