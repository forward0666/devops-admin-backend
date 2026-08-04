package com.backend.agent.service;

import com.backend.agent.entity.ModelEntity;
import com.backend.agent.mapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ModelService {

    @Autowired
    private ModelMapper modelMapper;

    public List<ModelEntity> list() {
        return modelMapper.selectList(null);
    }

    public ModelEntity getById(Integer id) {
        return modelMapper.selectById(id);
    }

    public ModelEntity create(ModelEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        modelMapper.insert(entity);
        return entity;
    }

    public ModelEntity update(Integer id, ModelEntity entity) {
        entity.setId(id);
        entity.setUpdatedAt(LocalDateTime.now());
        modelMapper.updateById(entity);
        return modelMapper.selectById(id);
    }

    public boolean delete(Integer id) {
        return modelMapper.deleteById(id) > 0;
    }
}