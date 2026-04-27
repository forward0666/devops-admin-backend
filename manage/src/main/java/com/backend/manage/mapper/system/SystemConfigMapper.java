package com.backend.manage.mapper.system;

import com.backend.manage.entity.system.SystemConfigEntity;
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
    @Select("SELECT * FROM settingss WHERE config_key = #{key}")
    SystemConfigEntity findByKey(@Param("key") String key);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM settingss ORDER BY config_key")
    List<SystemConfigEntity> findAll();

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM settingss WHERE is_public = true ORDER BY config_key")
    List<SystemConfigEntity> findPublicConfigs();

    @Insert("INSERT INTO settingss (config_key, config_value, config_type, description, is_public, created_at, updated_at) " +
            "VALUES (#{configKey}, #{configValue}, #{configType}, #{description}, #{isPublic}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SystemConfigEntity config);

    @Update("UPDATE settingss SET config_value = #{configValue}, config_type = #{configType}, " +
            "description = #{description}, is_public = #{isPublic}, updated_at = NOW() " +
            "WHERE config_key = #{configKey}")
    int update(SystemConfigEntity config);

    @Delete("DELETE FROM settingss WHERE config_key = #{key}")
    int deleteByKey(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM settingss WHERE config_key = #{key}")
    int countByKey(@Param("key") String key);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM settingss WHERE config_type = #{type} ORDER BY config_key")
    List<SystemConfigEntity> findByType(@Param("type") String type);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM settingss WHERE config_key LIKE CONCAT('%', #{pattern}, '%') ORDER BY config_key")
    List<SystemConfigEntity> searchByKeyPattern(@Param("pattern") String pattern);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM settingss WHERE config_key LIKE CONCAT(#{prefix}, '%') ORDER BY config_key")
    List<SystemConfigEntity> findByKeyPrefix(@Param("prefix") String prefix);
}
