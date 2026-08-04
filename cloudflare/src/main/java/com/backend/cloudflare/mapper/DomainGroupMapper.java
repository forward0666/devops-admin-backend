package com.backend.cloudflare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.backend.cloudflare.entity.DomainGroupEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DomainGroupMapper extends BaseMapper<DomainGroupEntity> {
}