package com.backend.manage.service;

import com.backend.manage.mapper.PositionMapper;
import com.backend.manage.entity.PositionEntity;
import com.backend.manage.service.PositionCacheService;
import com.backend.manage.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 职位服务类
 * 处理职位相关的业务逻辑
 */
@Slf4j
@Service
public class PositionService {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PositionMapper positionMapper;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private PositionCacheService positionCacheService;

    /**
     * 获取所有职位列表
     */
    public List<PositionEntity> getAllPositions() {
        log.info("正在获取所有职位列表");

        // 优先从Redis缓存获取职位列表
        if (cacheService.isRedisAvailable()) {
            List<PositionEntity> cachedPositions = positionCacheService.getCachedPositionsList();
            if (cachedPositions != null) {
                log.debug("从缓存中获取职位列表成功");
                return cachedPositions;
            }
        }

        try {
            List<PositionEntity> positions = positionMapper.findAll();

            // 将查询结果缓存到Redis中
            if (cacheService.isRedisAvailable()) {
                positionCacheService.cachePositionsList(positions);
            }

            log.info("成功获取 {} 个职位", positions.size());
            return positions;
        } catch (Exception e) {
            log.error("获取所有职位时发生错误", e);
            throw new RuntimeException("获取职位列表失败", e);
        }
    }

    /**
     * 根据ID获取职位
     */
    public PositionEntity getPositionById(Long id) {
        log.info("正在根据ID获取职位: {}", id);

        if (id == null) {
            log.warn("职位ID不能为空");
            return null;
        }

        // 优先从Redis缓存获取职位信息
        if (cacheService.isRedisAvailable()) {
            PositionEntity cachedPosition = positionCacheService.getCachedPosition(id);
            if (cachedPosition != null) {
                log.debug("从缓存中获取职位成功: {}", id);
                return cachedPosition;
            }
        }

        try {
            PositionEntity position = positionMapper.findById(id);
            if (position != null) {
                // 将查询结果缓存到Redis中
                if (cacheService.isRedisAvailable()) {
                    positionCacheService.cachePosition((PositionEntity) position);
                }

                log.info("成功获取职位: {}", position.getName());
                return position;
            } else {
                log.warn("找不到ID为 {} 的职位", id);
                return null;
            }
        } catch (Exception e) {
            log.error("根据ID获取职位时发生错误: {}", id, e);
            throw new RuntimeException("获取职位信息失败", e);
        }
    }

    /**
     * 创建新职位
     */
    public PositionEntity createPosition(PositionEntity position) {
        log.info("正在创建新职位: {}", position.getName());

        validatePositionForCreation(position);

        try {
            // 验证部门是否存在
            if (position.getDepartmentId() != null) {
                if (departmentService.getDepartmentById(position.getDepartmentId()) == null) {
                    throw new IllegalArgumentException("部门不存在");
                }
            }

            if (positionMapper.existsByCode(position.getCode())) {
                throw new IllegalArgumentException("职位代码 '" + position.getCode() + "' 已存在");
            }

            if (position.getUserCount() == null) {
                position.setUserCount(0);
            }

            position.setCreatedAt(LocalDateTime.now());
            position.setUpdatedAt(LocalDateTime.now());

            positionMapper.insert(position);

            // 清除相关缓存
            if (cacheService.isRedisAvailable()) {
                positionCacheService.clearAllPositionCache();
            }

            log.info("成功创建职位，ID: {}", position.getId());
            return position;
        } catch (Exception e) {
            log.error("创建职位时发生错误: {}", position.getName(), e);
            throw new RuntimeException("创建职位失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新职位
     */
    public PositionEntity updatePosition(PositionEntity position) {
        log.info("正在更新职位，ID: {}", position.getId());

        validatePositionForUpdate(position);

        try {
            if (!positionMapper.existsById(position.getId())) {
                log.warn("找不到要更新的职位，ID: {}", position.getId());
                return null;
            }

            // 验证部门是否存在
            if (position.getDepartmentId() != null) {
                if (departmentService.getDepartmentById(position.getDepartmentId()) == null) {
                    throw new IllegalArgumentException("部门不存在");
                }
            }

            if (positionMapper.existsByCodeExcludingId(position.getCode(), position.getId())) {
                throw new IllegalArgumentException("职位代码 '" + position.getCode() + "' 已存在");
            }

            position.setUpdatedAt(LocalDateTime.now());
            positionMapper.update(position);

            // 清除相关缓存
            if (cacheService.isRedisAvailable()) {
                positionCacheService.clearAllPositionCache();
            }

            log.info("成功更新职位: {}", position.getName());
            return getPositionById(position.getId());
        } catch (Exception e) {
            log.error("更新职位时发生错误，ID: {}", position.getId(), e);
            throw new RuntimeException("更新职位失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除职位
     */
    public boolean deletePosition(Long id) {
        log.info("正在删除职位，ID: {}", id);

        if (id == null) {
            log.warn("删除职位时职位ID不能为空");
            return false;
        }

        try {
            if (!positionMapper.existsById(id)) {
                log.warn("找不到要删除的职位，ID: {}", id);
                return false;
            }

            PositionEntity position = positionMapper.findById(id);
            if (position.getUserCount() != null && position.getUserCount() > 0) {
                throw new IllegalStateException("无法删除包含 " + position.getUserCount() + " 个用户的职位。请先重新分配用户。");
            }

            boolean deleted = positionMapper.deleteById(id) > 0;

            if (deleted) {
                if (cacheService.isRedisAvailable()) {
                    positionCacheService.clearAllPositionCache();
                }
                log.info("成功删除职位，ID: {}", id);
            } else {
                log.warn("删除职位失败，ID: {}", id);
            }

            return deleted;
        } catch (Exception e) {
            log.error("删除职位时发生错误，ID: {}", id, e);
            throw new RuntimeException("删除职位失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据部门ID获取职位列表
     */
    public List<PositionEntity> getPositionsByDepartmentId(Long departmentId) {
        log.info("获取部门ID {} 下的职位列表", departmentId);

        try {
            return positionMapper.findByDepartmentId(departmentId);
        } catch (Exception e) {
            log.error("根据部门ID获取职位列表时发生错误: {}", departmentId, e);
            throw new RuntimeException("获取部门职位列表失败", e);
        }
    }

    /**
     * 更新职位的用户数量
     */
    public void updateUserCount(Long positionId) {
        log.info("更新职位ID {} 的用户数量", positionId);
        try {
            positionMapper.updateUserCount(positionId);
        } catch (Exception e) {
            log.error("更新职位用户数量时发生错误: {}", positionId, e);
        }
    }

    // Private helper methods

    private void validatePositionForCreation(PositionEntity position) {
        if (position == null) {
            throw new IllegalArgumentException("职位不能为空");
        }

        if (position.getName() == null || position.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("职位名称不能为空");
        }

        if (position.getName().length() > 100) {
            throw new IllegalArgumentException("职位名称不能超过100个字符");
        }

        if (position.getCode() == null || position.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("职位代码不能为空");
        }

        if (position.getCode().length() > 50) {
            throw new IllegalArgumentException("职位代码不能超过50个字符");
        }

        if (position.getDescription() != null && position.getDescription().length() > 500) {
            throw new IllegalArgumentException("职位描述不能超过500个字符");
        }

        if (position.getLevel() == null || position.getLevel() < 1 || position.getLevel() > 5) {
            throw new IllegalArgumentException("职位级别必须在1-5之间");
        }

        if (position.getStatus() == null) {
            position.setStatus("active");
        }
    }

    private void validatePositionForUpdate(PositionEntity position) {
        if (position == null) {
            throw new IllegalArgumentException("职位不能为空");
        }

        if (position.getId() == null) {
            throw new IllegalArgumentException("更新职位需要提供职位ID");
        }

        validatePositionForCreation(position);
    }
}
