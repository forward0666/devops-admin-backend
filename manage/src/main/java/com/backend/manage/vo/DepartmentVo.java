package com.backend.manage.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门视图对象
 * 用于返回部门详细信息
 *
 * 设计特点：
 * 1. 使用 Java 21 record 实现不可变数据结构
 * 2. 包含部门基本信息和统计数据
 * 3. 包含最近加入的用户列表
 * 4. 自动生成 getter 方法（如 id(), name()）
 * 5. 自动实现 equals(), hashCode(), toString()
 *
 * @author Backend Team
 * @version 2.0.0
 */
public record DepartmentVo(
        /**
         * 部门唯一标识符
         */
        Long id,

        /**
         * 部门名称
         */
        String name,

        /**
         * 部门描述
         */
        String description,

        /**
         * 部门经理 ID
         */
        Long managerId,

        /**
         * 部门经理姓名
         */
        String managerName,

        /**
         * 部门用户数量
         */
        Integer userCount,

        /**
         * 活跃项目数量
         */
        Integer activeProjects,

        /**
         * 已完成项目数量
         */
        Integer completedProjects,

        /**
         * 创建时间
         */
        LocalDateTime createdAt,

        /**
         * 更新时间
         */
        LocalDateTime updatedAt,

        /**
         * 最近加入的用户列表
         */
        List<UserVo> recentUsers
) {}
