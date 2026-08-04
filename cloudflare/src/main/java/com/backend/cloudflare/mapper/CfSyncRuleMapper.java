package com.backend.cloudflare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.backend.cloudflare.entity.CfSyncRuleEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CfSyncRuleMapper extends BaseMapper<CfSyncRuleEntity> {
}