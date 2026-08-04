package com.backend.cloudflare.mapper;

import com.backend.cloudflare.entity.CfCacheRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CfCacheRuleMapper {
    List<CfCacheRuleEntity> findByProjectId(@Param("projectId") Long projectId);
    List<CfCacheRuleEntity> findByProjectIdAndEnv(@Param("projectId") Long projectId, @Param("env") String env);
    CfCacheRuleEntity findById(@Param("id") Long id);
    CfCacheRuleEntity findDuplicateName(@Param("projectId") Long projectId, @Param("name") String name, @Param("env") String env, @Param("excludeId") Long excludeId);
    int insert(CfCacheRuleEntity entity);
    int update(CfCacheRuleEntity entity);
    int deleteById(@Param("id") Long id);
}