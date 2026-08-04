package com.backend.manage.mapper;

import com.backend.manage.entity.ModuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModuleMapper {
    List<ModuleEntity> findAll();

    ModuleEntity findById(@Param("id") Long id);

    List<ModuleEntity> findByRoleId(@Param("roleId") Long roleId);

    List<ModuleEntity> findByCategory(@Param("category") String category);

    List<ModuleEntity> findByParentId(@Param("parentId") Long parentId);

    int insert(ModuleEntity module);

    int update(ModuleEntity module);

    int deleteById(@Param("id") Long id);

    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);
}