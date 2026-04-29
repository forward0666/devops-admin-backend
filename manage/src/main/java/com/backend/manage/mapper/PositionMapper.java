package com.backend.manage.mapper;

import com.backend.manage.entity.PositionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PositionMapper {

    List<PositionEntity> findAll();

    PositionEntity findById(@Param("id") Long id);

    PositionEntity findByCode(@Param("code") String code);

    int insert(PositionEntity position);

    int update(PositionEntity position);

    int deleteById(@Param("id") Long id);

    boolean existsById(@Param("id") Long id);

    boolean existsByCode(@Param("code") String code);

    boolean existsByCodeExcludingId(@Param("code") String code, @Param("id") Long id);

    List<PositionEntity> findByDepartmentId(@Param("departmentId") Long departmentId);

    void updateUserCount(@Param("id") Long id);
}
