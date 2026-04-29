package com.backend.manage.mapper;

import com.backend.manage.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper {

    List<RoleEntity> findAll();

    RoleEntity findById(@Param("id") Long id);

    RoleEntity findByCode(@Param("code") String code);

    int insert(RoleEntity role);

    int update(RoleEntity role);

    int deleteById(@Param("id") Long id);

    boolean existsById(@Param("id") Long id);

    boolean existsByCode(@Param("code") String code);

    boolean existsByCodeExcludingId(@Param("code") String code, @Param("id") Long id);
}
