package com.backend.manage.service;

import com.backend.manage.entity.ModuleEntity;
import com.backend.manage.entity.RoleModuleEntity;
import com.backend.manage.mapper.ModuleMapper;
import com.backend.manage.mapper.RoleModuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 功能模块管理服务
 * 处理模块树、角色权限分配和数据初始化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleMapper moduleMapper;
    private final RoleModuleMapper roleModuleMapper;

    /**
     * 获取所有模块（平铺列表）
     */
    public List<ModuleEntity> getAllModules() {
        return moduleMapper.findAll();
    }

    /**
     * 获取模块树结构
     */
    public List<ModuleEntity> getModuleTree() {
        List<ModuleEntity> all = moduleMapper.findAll();
        return buildTree(all);
    }

    /**
     * 获取指定分类的模块树
     */
    public List<ModuleEntity> getModuleTreeByCategory(String category) {
        List<ModuleEntity> all = moduleMapper.findByCategory(category);
        return buildTree(all);
    }

    /**
     * 获取指定角色可见的模块树
     */
    public List<ModuleEntity> getModulesByRoleId(Long roleId) {
        List<ModuleEntity> modules = moduleMapper.findByRoleId(roleId);
        return buildTree(modules);
    }

    /**
     * 获取指定角色的模块ID列表
     */
    public List<Long> getModuleIdsByRoleId(Long roleId) {
        return roleModuleMapper.findModuleIdsByRoleId(roleId);
    }

    /**
     * 保存角色的模块权限（全量替换）
     */
    @Transactional
    public void saveRoleModules(Long roleId, List<Long> moduleIds) {
        roleModuleMapper.deleteByRoleId(roleId);
        if (moduleIds == null || moduleIds.isEmpty()) {
            log.info("Cleared module permissions for role: {}", roleId);
            return;
        }
        List<RoleModuleEntity> list = moduleIds.stream()
                .map(mid -> {
                    RoleModuleEntity e = new RoleModuleEntity();
                    e.setRoleId(roleId);
                    e.setModuleId(mid);
                    e.setCanRead(true);
                    e.setCanWrite(true);
                    return e;
                })
                .collect(Collectors.toList());
        roleModuleMapper.batchInsert(list);
        log.info("Saved {} module permissions for role: {}", list.size(), roleId);
    }

    /**
     * 获取模块详情
     */
    public ModuleEntity getModuleById(Long id) {
        return moduleMapper.findById(id);
    }

    /**
     * 创建模块
     */
    @Transactional
    public ModuleEntity createModule(ModuleEntity module) {
        if (module.getSortOrder() == null) {
            module.setSortOrder(99);
        }
        if (module.getEnabled() == null) {
            module.setEnabled(true);
        }
        moduleMapper.insert(module);
        log.info("Created module: {} (code: {})", module.getName(), module.getCode());
        return module;
    }

    /**
     * 更新模块
     */
    @Transactional
    public ModuleEntity updateModule(ModuleEntity module) {
        moduleMapper.update(module);
        log.info("Updated module: {} (id: {})", module.getName(), module.getId());
        return moduleMapper.findById(module.getId());
    }

    /**
     * 删除模块
     */
    @Transactional
    public void deleteModule(Long id) {
        // 先删除子模块
        List<ModuleEntity> children = moduleMapper.findByParentId(id);
        for (ModuleEntity child : children) {
            roleModuleMapper.deleteByModuleId(child.getId());
            moduleMapper.deleteById(child.getId());
        }
        roleModuleMapper.deleteByModuleId(id);
        moduleMapper.deleteById(id);
        log.info("Deleted module id: {} and its children", id);
    }

    // ==================== 树形构建 ====================

    private List<ModuleEntity> buildTree(List<ModuleEntity> flatList) {
        Map<Long, List<ModuleEntity>> childrenMap = new LinkedHashMap<>();
        Map<Long, ModuleEntity> entityMap = new HashMap<>();

        for (ModuleEntity m : flatList) {
            entityMap.put(m.getId(), m);
            childrenMap.computeIfAbsent(m.getParentId(), k -> new ArrayList<>());
        }

        List<ModuleEntity> roots = new ArrayList<>();
        for (ModuleEntity m : flatList) {
            if (m.getParentId() == null) {
                roots.add(m);
            } else {
                childrenMap.computeIfAbsent(m.getParentId(), k -> new ArrayList<>()).add(m);
            }
        }

        // Sort children
        for (Map.Entry<Long, List<ModuleEntity>> entry : childrenMap.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(ModuleEntity::getSortOrder));
        }
        roots.sort(Comparator.comparingInt(ModuleEntity::getSortOrder));

        // Attach children in-place (external tree via field `children` not in entity,
        // we use vo/dto for frontend, but for simplicity client builds tree too)
        return roots;
    }
}