package com.backend.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.backend.domain.entity.DomainEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DomainMapper extends BaseMapper<DomainEntity> {
}