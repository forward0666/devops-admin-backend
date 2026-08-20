// 权限策略 - 对应阿里云的 Policy
// 核心概念：
//   Action     - 操作 (如 "project:create", "user:delete")
//   Resource   - 资源标识 (如 "project:*", "user:123")
//   Effect     - Allow / Deny
//   Condition  - 条件 (如 orgId=xxx)

package com.backend.security.authorization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 权限策略 — 对应阿里云 Policy 模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyStatement {
    
    /** 允许或拒绝 */
    private Effect effect;
    
    /** 操作列表，如 ["project:create", "project:edit"] */
    private List<String> actions;
    
    /** 资源列表，如 ["project:*", "project:123"] */
    private List<String> resources;
    
    /** 条件（可选），如部门/组织范围 */
    private Map<String, String> conditions;
    
    public enum Effect {
        Allow, Deny
    }
}