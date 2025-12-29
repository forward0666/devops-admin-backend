package com.backend.manage.mapper;

import com.backend.manage.model.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PositionMapper {

    List<Position> findAll();

    Position findById(@Param("id") Long id);

    Position findByCode(@Param("code") String code);

    List<Position> findByDepartmentId(@Param("departmentId") Long departmentId);

    int insert(Position position);

    int update(Position position);

    int deleteById(@Param("id") Long id);

    boolean existsById(@Param("id") Long id);

    boolean existsByCode(@Param("code") String code);

    boolean existsByCodeExcludingId(@Param("code") String code, @Param("id") Long id);

    int updateUserCount(@Param("id") Long id);
}
