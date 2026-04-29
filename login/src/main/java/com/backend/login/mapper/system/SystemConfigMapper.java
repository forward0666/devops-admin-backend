package com.backend.login.mapper.system;

import com.backend.login.entity.system.SettingEntity;
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
    @Select("SELECT * FROM setting WHERE config_key = #{key}")
    SettingEntity findByKey(@Param("key") String key);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM setting ORDER BY config_key")
    List<SettingEntity> findAll();

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM setting WHERE is_public = true ORDER BY config_key")
    List<SettingEntity> findPublicConfigs();

    @Insert("INSERT INTO setting (config_key, config_value, config_type, description, is_public, created_at, updated_at) " +
            "VALUES (#{configKey}, #{configValue}, #{configType}, #{description}, #{isPublic}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SettingEntity config);

    @Update("UPDATE setting SET config_value = #{configValue}, config_type = #{configType}, " +
            "description = #{description}, is_public = #{isPublic}, updated_at = NOW() " +
            "WHERE config_key = #{configKey}")
    int update(SettingEntity config);

    @Delete("DELETE FROM setting WHERE config_key = #{key}")
    int deleteByKey(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM setting WHERE config_key = #{key}")
    int countByKey(@Param("key") String key);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM setting WHERE config_type = #{type} ORDER BY config_key")
    List<SettingEntity> findByType(@Param("type") String type);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM setting WHERE config_key LIKE CONCAT('%', #{pattern}, '%') ORDER BY config_key")
    List<SettingEntity> searchByKeyPattern(@Param("pattern") String pattern);

    @ResultMap("systemConfigResultMap")
    @Select("SELECT * FROM setting WHERE config_key LIKE CONCAT(#{prefix}, '%') ORDER BY config_key")
    List<SettingEntity> findByKeyPrefix(@Param("prefix") String prefix);
}
