package com.backend.login.mapper;

import com.backend.login.entity.SettingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SettingMapper {

    SettingEntity findByKey(@Param("key") String key);

    List<SettingEntity> findAll();

    List<SettingEntity> findPublicConfigs();

    List<SettingEntity> findByType(@Param("type") String type);

    List<SettingEntity> searchByKeyPattern(@Param("pattern") String pattern);

    List<SettingEntity> findByKeyPrefix(@Param("prefix") String prefix);

    int countByKey(@Param("key") String key);

    int insert(SettingEntity setting);

    int update(SettingEntity setting);

    int deleteByKey(@Param("key") String key);
}
