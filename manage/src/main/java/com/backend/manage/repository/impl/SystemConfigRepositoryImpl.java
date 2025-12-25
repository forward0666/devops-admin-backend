package com.backend.manage.repository.impl;

import com.backend.manage.mapper.SystemConfigMapper;
import com.backend.manage.model.SystemConfig;
import com.backend.manage.repository.SystemConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class SystemConfigRepositoryImpl implements SystemConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigRepositoryImpl.class);

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getConfigValue(String key, String defaultValue) {
        try {
            SystemConfig config = systemConfigMapper.findByKey(key);
            return config != null ? config.getConfigValue() : defaultValue;
        } catch (Exception e) {
            logger.error("Error getting config value for key: {}", key, e);
            return defaultValue;
        }
    }

    @Override
    public void setConfigValue(String key, String value) {
        try {
            SystemConfig existingConfig = systemConfigMapper.findByKey(key);
            if (existingConfig != null) {
                existingConfig.setConfigValue(value);
                existingConfig.setUpdatedAt(LocalDateTime.now());
                systemConfigMapper.update(existingConfig);
                logger.info("Updated config: {} = {}", key, value);
            } else {
                SystemConfig newConfig = new SystemConfig();
                newConfig.setConfigKey(key);
                newConfig.setConfigValue(value);
                newConfig.setConfigType("STRING");
                newConfig.setDescription("Auto-generated configuration");
                newConfig.setPublic(false);
                newConfig.setCreatedAt(LocalDateTime.now());
                newConfig.setUpdatedAt(LocalDateTime.now());
                systemConfigMapper.insert(newConfig);
                logger.info("Created new config: {} = {}", key, value);
            }
        } catch (Exception e) {
            logger.error("Error setting config value for key: {}", key, e);
            throw new RuntimeException("Failed to set config value", e);
        }
    }

    @Override
    public <T> T getConfigObject(String key, Class<T> clazz, T defaultValue) {
        try {
            SystemConfig config = systemConfigMapper.findByKey(key);
            if (config == null || config.getConfigValue() == null) {
                return defaultValue;
            }
            return objectMapper.readValue(config.getConfigValue(), clazz);
        } catch (Exception e) {
            logger.error("Error getting config object for key: {}", key, e);
            return defaultValue;
        }
    }

    @Override
    public <T> T getConfigObject(String key, TypeReference<T> typeRef, T defaultValue) {
        try {
            SystemConfig config = systemConfigMapper.findByKey(key);
            if (config == null || config.getConfigValue() == null) {
                return defaultValue;
            }
            return objectMapper.readValue(config.getConfigValue(), typeRef);
        } catch (Exception e) {
            logger.error("Error getting config object with TypeReference for key: {}", key, e);
            return defaultValue;
        }
    }

    @Override
    public void setConfigObject(String key, Object value) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            setConfigValue(key, jsonValue);
        } catch (Exception e) {
            logger.error("Error setting config object for key: {}", key, e);
            throw new RuntimeException("Failed to set config object", e);
        }
    }

    @Override
    public List<SystemConfig> getAllConfigs() {
        try {
            return systemConfigMapper.findAll();
        } catch (Exception e) {
            logger.error("Error getting all configs", e);
            throw new RuntimeException("Failed to get all configs", e);
        }
    }

    @Override
    public List<SystemConfig> getPublicConfigs() {
        try {
            return systemConfigMapper.findPublicConfigs();
        } catch (Exception e) {
            logger.error("Error getting public configs", e);
            throw new RuntimeException("Failed to get public configs", e);
        }
    }

    @Override
    public SystemConfig getConfigByKey(String key) {
        try {
            return systemConfigMapper.findByKey(key);
        } catch (Exception e) {
            logger.error("Error getting config by key: {}", key, e);
            return null;
        }
    }

    @Override
    public SystemConfig createConfig(SystemConfig config) {
        try {
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            systemConfigMapper.insert(config);
            logger.info("Created new config: {}", config.getConfigKey());
            return config;
        } catch (Exception e) {
            logger.error("Error creating config: {}", config.getConfigKey(), e);
            throw new RuntimeException("Failed to create config", e);
        }
    }

    @Override
    public SystemConfig updateConfig(SystemConfig config) {
        try {
            config.setUpdatedAt(LocalDateTime.now());
            systemConfigMapper.update(config);
            logger.info("Updated config: {}", config.getConfigKey());
            return config;
        } catch (Exception e) {
            logger.error("Error updating config: {}", config.getConfigKey(), e);
            throw new RuntimeException("Failed to update config", e);
        }
    }

    @Override
    public boolean deleteConfig(String key) {
        try {
            int deleted = systemConfigMapper.deleteByKey(key);
            if (deleted > 0) {
                logger.info("Deleted config: {}", key);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error deleting config: {}", key, e);
            throw new RuntimeException("Failed to delete config", e);
        }
    }

    @Override
    public boolean configExists(String key) {
        try {
            return systemConfigMapper.findByKey(key) != null;
        } catch (Exception e) {
            logger.error("Error checking if config exists: {}", key, e);
            return false;
        }
    }
}
