package com.admin.manage.mapper;

import com.admin.manage.model.SystemConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SystemConfigMapper {

    @Results(id = "systemConfigResultMap", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "configKey", column = "config_key"),
        @Result(property = "configValue", column = "config_value"),
        @Result(property = "configType", column = "config_type"),
        @Result(property = "description", column = "description"),
        @Result(property = "isPublic", column = "is_public"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    @Select("SELECT * FROM system_configs WHERE config_key = #{key}")
    SystemConfig findByKey(@Param("key") String key);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM system_configs ORDER BY config_key")
    List<SystemConfig> findAll();

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM system_configs WHERE is_public = true ORDER BY config_key")
    List<SystemConfig> findPublicConfigs();

    @Insert("INSERT INTO system_configs (config_key, config_value, config_type, description, is_public, created_at, updated_at) " +
            "VALUES (#{configKey}, #{configValue}, #{configType}, #{description}, #{isPublic}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SystemConfig config);

    @Update("UPDATE system_configs SET config_value = #{configValue}, config_type = #{configType}, " +
            "description = #{description}, is_public = #{isPublic}, updated_at = NOW() " +
            "WHERE config_key = #{configKey}")
    int update(SystemConfig config);

    @Delete("DELETE FROM system_configs WHERE config_key = #{key}")
    int deleteByKey(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM system_configs WHERE config_key = #{key}")
    int countByKey(@Param("key") String key);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM system_configs WHERE config_type = #{type} ORDER BY config_key")
    List<SystemConfig> findByType(@Param("type") String type);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM system_configs WHERE config_key LIKE CONCAT('%', #{pattern}, '%') ORDER BY config_key")
    List<SystemConfig> searchByKeyPattern(@Param("pattern") String pattern);
}
