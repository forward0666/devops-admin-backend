package com.backend.agent.service;

import com.backend.agent.entity.ToolEntity;
import com.backend.agent.mapper.ToolMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ToolService {

    @Autowired
    private ToolMapper toolMapper;

    public List<ToolEntity> list() {
        return toolMapper.selectList(null);
    }

    public ToolEntity getById(Integer id) {
        return toolMapper.selectById(id);
    }

    public ToolEntity create(ToolEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        toolMapper.insert(entity);
        return entity;
    }

    public ToolEntity update(Integer id, ToolEntity entity) {
        entity.setId(id);
        entity.setUpdatedAt(LocalDateTime.now());
        toolMapper.updateById(entity);
        return toolMapper.selectById(id);
    }

    public boolean delete(Integer id) {
        return toolMapper.deleteById(id) > 0;
    }
}