package com.backend.user.mapper;

import com.backend.user.entity.ProjectMemberEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMemberMapper {

    List<ProjectMemberEntity> findByProjectId(@Param("projectId") Long projectId);

    ProjectMemberEntity findById(@Param("id") Long id);

    ProjectMemberEntity findByProjectIdAndUserId(@Param("projectId") Long projectId, @Param("userId") Long userId);

    ProjectMemberEntity findByProjectIdAndTgUsername(@Param("projectId") Long projectId, @Param("tgUsername") String tgUsername);

    int insert(ProjectMemberEntity entity);

    int update(ProjectMemberEntity entity);

    int deleteById(@Param("id") Long id);

    int deleteByProjectIdAndUserId(@Param("projectId") Long projectId, @Param("userId") Long userId);

    int existsByProjectIdAndUserId(@Param("projectId") Long projectId, @Param("userId") Long userId);

    int countByProjectId(@Param("projectId") Long projectId);

    List<Long> findProjectIdsByUserId(@Param("userId") Long userId);
}
