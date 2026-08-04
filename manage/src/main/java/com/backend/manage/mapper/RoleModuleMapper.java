package com.backend.manage.mapper;

import com.backend.manage.entity.RoleModuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleModuleMapper {
    List<RoleModuleEntity> findByRoleId(@Param("roleId") Long roleId);

    List<Long> findModuleIdsByRoleId(@Param("roleId") Long roleId);

    int insert(RoleModuleEntity entity);

    int batchInsert(@Param("list") List<RoleModuleEntity> list);

    int deleteByRoleId(@Param("roleId") Long roleId);

    int deleteByModuleId(@Param("moduleId") Long moduleId);
}