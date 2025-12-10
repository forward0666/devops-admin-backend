package com.backend.bot.entity;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * 用户会话状态实体类
 * 
 * 该类用于存储用户在与 Bot 交互过程中的会话状态信息，支持多步操作的上下文管理。
 * 会话状态机制允许 Bot 在处理复杂操作时记住用户的当前操作步骤和上下文信息。
 * 
 * 设计特点：
 * 1. 不可变对象设计，使用 final 字段确保线程安全
 * 2. Builder 模式提供灵活的对象创建方式
 * 3. 时间戳自动记录，支持会话过期管理
 * 4. 状态机制支持复杂的多步交互流程
 * 
 * 使用场景：
 * - IP 白名单添加流程（等待用户输入 IP）
 * - 多步骤表单填写
 * - 向导式操作流程
 * - 上下文相关的对话流程
 * 
 * 会话生命周期：
 * 1. 用户开始特定操作时创建会话
 * 2. 每个步骤中更新会话状态
 * 3. 操作完成或超时后清理会话
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Data              // Lombok 注解，自动生成 getter/setter、toString 等方法
@Builder           // Lombok 注解，提供 Builder 模式构建对象
@ToString          // Lombok 注解，自动生成 toString 方法
public class UserSessionEntity {
    
    /**
     * 用户 Telegram ID
     * 
     * 唯一标识用户的 Telegram 用户 ID，作为会话的键值。
     * 每个用户在任何时刻只能有一个活跃的会话状态。
     */
    private final Long userId;

    /**
     * 当前会话状态
     * 
     * 表示用户当前所处的操作步骤或等待状态。
     * 
     * 常见状态值：
     * - AWAITING_FRONTEND_IP: 等待用户输入前端 IP
     * - AWAITING_BACKEND_IP: 等待用户输入后端 IP
     * - AWAITING_CONFIRMATION: 等待用户确认操作
     * - IDLE: 空闲状态，无特殊操作进行中
     * 
     * 状态由处理器根据业务逻辑定义和更新。
     */
    private final String currentState;

    /**
     * 参考消息 ID
     * 
     * 记录触发当前状态的消息 ID，用于：
     * 1. 状态恢复时的上下文关联
     * 2. 编辑或回复原始消息
     * 3. 提供更丰富的用户交互体验
     */
    private final Long referenceMessageId;

    /**
     * 状态设置时间戳
     * 
     * 记录会话状态的创建时间，用于：
     * 1. 会话过期管理（超时自动清理）
     * 2. 用户会话时长统计
     * 3. 调试和问题排查
     * 
     * 使用 System.currentTimeMillis() 获取精确到毫秒的时间戳。
     */
    private final long timestamp = System.currentTimeMillis();

    /**
     * 获取当前状态
     * 
     * 提供获取会话状态的便捷方法。
     * 由于 UserSessionEntity 使用了 Builder 模式和 final 字段，
     * TextUpdateHandler 等处理器需要通过此方法访问状态信息。
     * 
     * 设计考虑：
     * - 提供明确的语义化方法名
     * - 避免直接访问字段，提高封装性
     * - 便于未来扩展状态获取逻辑（如状态规范化）
     * 
     * @return 当前会话状态字符串
     */
    public String getState() {
        return currentState;
    }
}