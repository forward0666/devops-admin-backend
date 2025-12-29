package com.backend.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门请求数据传输对象
 * 用于创建或更新部门信息
 *
 * 设计特点：
 * 1. 使用 Lombok @Data 注解自动生成 getter/setter
 * 2. 使用验证注解确保输入数据有效性
 * 3. 支持部门基本信息的创建和更新
 *
 * @author Backend Team
 * @version 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRequestDto {

    /**
     * 部门名称
     *
     * 验证规则：
     * - 不能为空
     * - 不能超过100个字符
     */
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 100, message = "部门名称不能超过100个字符")
    private String name;

    /**
     * 部门描述
     *
     * 验证规则：不能超过500个字符
     */
    @Size(max = 500, message = "部门描述不能超过500个字符")
    private String description;

    /**
     * 部门经理 ID
     *
     * 关联到用户表的主键
     */
    private Long managerId;
}