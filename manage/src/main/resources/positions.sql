-- 职位表 (positions)
-- 用于存储系统中的职位信息
CREATE TABLE IF NOT EXISTS `positions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '职位ID',
  `name` VARCHAR(100) NOT NULL COMMENT '职位名称',
  `code` VARCHAR(50) NOT NULL COMMENT '职位代码（唯一标识）',
  `department_id` BIGINT UNSIGNED COMMENT '所属部门ID',
  `level` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '职位级别：1-初级，2-中级，3-高级，4-经理，5-总监',
  `description` TEXT COMMENT '职位描述',
  `status` ENUM('active', 'inactive') NOT NULL DEFAULT 'active' COMMENT '状态：active-启用，inactive-禁用',
  `user_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '该职位的用户数量',
  `created_by` BIGINT UNSIGNED COMMENT '创建人ID',
  `updated_by` BIGINT UNSIGNED COMMENT '更新人ID',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TIMESTAMP NULL COMMENT '删除时间（软删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_department_id` (`department_id`),
  KEY `idx_level` (`level`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='职位表';

-- 插入默认职位数据
-- 注意：这里假设 departments 表已存在并有数据
-- 如果 departments 表不存在，请先执行 departments_table.sql

-- 技术部职位
INSERT INTO `positions` (`name`, `code`, `department_id`, `level`, `description`, `status`, `user_count`) VALUES
('技术总监', 'CTO', (SELECT `id` FROM `departments` WHERE `name` = '技术部' LIMIT 1), 5, '负责技术战略和团队管理，制定技术发展方向', 'active', 0),
('架构师', 'SA', (SELECT `id` FROM `departments` WHERE `name` = '技术部' LIMIT 1), 4, '负责系统架构设计和技术选型', 'active', 0),
('高级工程师', 'SE', (SELECT `id` FROM `departments` WHERE `name` = '技术部' LIMIT 1), 3, '负责核心模块开发和代码审查', 'active', 0),
('中级工程师', 'ME', (SELECT `id` FROM `departments` WHERE `name` = '技术部' LIMIT 1), 2, '负责业务功能开发和单元测试', 'active', 0),
('初级工程师', 'JE', (SELECT `id` FROM `departments` WHERE `name` = '技术部' LIMIT 1), 1, '负责基础功能开发、测试和文档编写', 'active', 0),
('测试工程师', 'TE', (SELECT `id` FROM `departments` WHERE `name` = '技术部' LIMIT 1), 2, '负责系统测试和质量保证', 'active', 0),
('运维工程师', 'OE', (SELECT `id` FROM `departments` WHERE `name` = '技术部' LIMIT 1), 2, '负责系统运维和监控', 'active', 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 产品部职位
INSERT INTO `positions` (`name`, `code`, `department_id`, `level`, `description`, `status`, `user_count`) VALUES
('产品总监', 'CPO', (SELECT `id` FROM `departments` WHERE `name` = '产品部' LIMIT 1), 5, '负责产品战略和产品团队管理', 'active', 0),
('产品经理', 'PM', (SELECT `id` FROM `departments` WHERE `name` = '产品部' LIMIT 1), 3, '负责产品规划和需求分析', 'active', 0),
('产品助理', 'PA', (SELECT `id` FROM `departments` WHERE `name` = '产品部' LIMIT 1), 1, '协助产品经理进行产品设计和需求跟进', 'active', 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 人事部职位
INSERT INTO `positions` (`name`, `code`, `department_id`, `level`, `description`, `status`, `user_count`) VALUES
('人事总监', 'CHRO', (SELECT `id` FROM `departments` WHERE `name` = '人事部' LIMIT 1), 5, '负责人力资源战略和团队管理', 'active', 0),
('人事经理', 'HRM', (SELECT `id` FROM `departments` WHERE `name` = '人事部' LIMIT 1), 2, '负责人力资源管理和招聘', 'active', 0),
('人事专员', 'HRS', (SELECT `id` FROM `departments` WHERE `name` = '人事部' LIMIT 1), 1, '负责日常人事事务和员工关系', 'active', 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 财务部职位
INSERT INTO `positions` (`name`, `code`, `department_id`, `level`, `description`, `status`, `user_count`) VALUES
('财务总监', 'CFO', (SELECT `id` FROM `departments` WHERE `name` = '财务部' LIMIT 1), 5, '负责财务战略和财务团队管理', 'active', 0),
('财务经理', 'FM', (SELECT `id` FROM `departments` WHERE `name` = '财务部' LIMIT 1), 2, '负责财务管理和报表分析', 'active', 0),
('会计', 'ACC', (SELECT `id` FROM `departments` WHERE `name` = '财务部' LIMIT 1), 1, '负责日常会计事务', 'active', 0),
('出纳', 'CAS', (SELECT `id` FROM `departments` WHERE `name` = '财务部' LIMIT 1), 1, '负责日常资金管理', 'active', 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 运营部职位
INSERT INTO `positions` (`name`, `code`, `department_id`, `level`, `description`, `status`, `user_count`) VALUES
('运营总监', 'COO', (SELECT `id` FROM `departments` WHERE `name` = '运营部' LIMIT 1), 5, '负责运营战略和运营团队管理', 'active', 0),
('运营经理', 'OM', (SELECT `id` FROM `departments` WHERE `name` = '运营部' LIMIT 1), 2, '负责日常运营管理', 'active', 0),
('运营专员', 'OS', (SELECT `id` FROM `departments` WHERE `name` = '运营部' LIMIT 1), 1, '负责具体运营事务的执行', 'active', 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 销售部职位
INSERT INTO `positions` (`name`, `code`, `department_id`, `level`, `description`, `status`, `user_count`) VALUES
('销售总监', 'CSO', (SELECT `id` FROM `departments` WHERE `name` = '销售部' LIMIT 1), 5, '负责销售战略和销售团队管理', 'active', 0),
('销售经理', 'SM', (SELECT `id` FROM `departments` WHERE `name` = '销售部' LIMIT 1), 2, '负责销售管理和客户关系维护', 'active', 0),
('销售代表', 'SR', (SELECT `id` FROM `departments` WHERE `name` = '销售部' LIMIT 1), 1, '负责具体销售工作', 'active', 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 如果需要外键约束，请在确保 departments 表存在后手动添加：
-- ALTER TABLE `positions`
--   ADD CONSTRAINT `fk_positions_department`
--   FOREIGN KEY (`department_id`)
--   REFERENCES `departments` (`id`)
--   ON DELETE SET NULL
--   ON UPDATE CASCADE;
