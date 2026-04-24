package com.backend.manage.mapper.system;

import com.backend.manage.entity.system.ProjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for Project operations using XML configuration
 */
@Mapper
public interface ProjectMapper {

    List<ProjectEntity> findAll();

    ProjectEntity findById(@Param("id") Long id);

    int insert(ProjectEntity project);

    int update(ProjectEntity project);

    int deleteById(@Param("id") Long id);

    int existsById(@Param("id") Long id);

    int existsByName(@Param("name") String name);

    List<ProjectEntity> search(@Param("query") String query);
}
