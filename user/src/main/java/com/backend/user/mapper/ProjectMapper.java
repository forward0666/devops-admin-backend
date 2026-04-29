package com.backend.user.mapper;

import com.backend.user.entity.ProjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMapper {

    List<ProjectEntity> findAll();

    ProjectEntity findById(@Param("id") Long id);
}
