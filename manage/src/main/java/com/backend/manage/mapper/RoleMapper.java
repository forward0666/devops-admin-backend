package com.backend.manage.mapper;

import com.backend.manage.model.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper {

    List<Role> findAll();

    Role findById(@Param("id") Long id);

    Role findByCode(@Param("code") String code);

    int insert(Role role);

    int update(Role role);

    int deleteById(@Param("id") Long id);

    boolean existsById(@Param("id") Long id);

    boolean existsByCode(@Param("code") String code);

    boolean existsByCodeExcludingId(@Param("code") String code, @Param("id") Long id);

    List<String> findPermissionsByRoleId(@Param("roleId") Long roleId);

    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionCode") String permissionCode);

    int deleteRolePermissions(@Param("roleId") Long roleId);
}
