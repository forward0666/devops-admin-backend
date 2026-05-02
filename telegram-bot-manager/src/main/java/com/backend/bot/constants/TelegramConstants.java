package com.backend.bot.constants;

/**
 * Telegram 相关常量统一管理类
 * 
 * 该类集中管理项目中所有 Telegram 相关的常量，包括 API 路径、
 * 超时设置、消息模板、错误消息等。遵循 DRY 原则，避免硬编码常量分散在代码中。
 * 
 * 常量分类：
 * 1. API 路径 - Telegram API 端点路径模板
 * 2. 超时设置 - 各种操作的默认超时时间
 * 3. 消息模板 - 标准化消息文本模板
 * 4. 错误消息 - 标准化错误提示文本
 * 5. 限制设置 - API 和业务限制常量
 * 
 * 使用原则：
 * 1. 所有硬编码常量应迁移到此类
 * 2. 常量名称应使用清晰、描述性的命名
 * 3. 常量应按功能分组，便于维护
 * 4. 避免在业务代码中直接使用字符串字面量
 * 
 * @author Backend Team
 * @version 1.0.0
 */
public final class TelegramConstants {

    public static final String COMMAND_START = "/start";
    public static final String COMMAND_CANCEL = "/new";
    public static final String COMMAND_HELP = "/help";
    public static final String COMMAND_ABOUT = "/about";
    public static final String COMMAND_SETTINGS = "/settings";


    /**
     * 群聊中需要自动清理的命令列表
     * 
     * 只需修改这个数组，即可添加或删除需要清理的命令
     * 格式："/command"，例如 "/help"、"/settings"
     */
    public static final String[] GROUP_COMMANDS_TO_CLEANUP = {
        "/start",
        "/new",
        "/help",
        "/about",
        "/settings"
    };

    /**
     * 私有构造函数，防止工具类实例化
     */
    private TelegramConstants() {}

    // ==================== API 路径常量 ====================
    
    /**
     * Telegram API 路径模板
     * 
     * 格式："/bot{token}/{method}"
     * 使用示例：String.format(API_PATH_TEMPLATE, token, "sendMessage")
     */
    public static final String API_PATH_TEMPLATE = "/bot%s/%s";
    
    /**
     * 设置 Webhook API 方法名
     */
    public static final String API_METHOD_SET_WEBHOOK = "setWebhook";
    
    /**
     * 发送消息 API 方法名
     */
    public static final String API_METHOD_SEND_MESSAGE = "sendMessage";
    
    /**
     * 删除消息 API 方法名
     */
    public static final String API_METHOD_DELETE_MESSAGE = "deleteMessage";
    
    /**
     * 编辑消息 API 方法名
     */
    public static final String API_METHOD_EDIT_MESSAGE_TEXT = "editMessageText";
    
    /**
     * Webhook 端点路径后缀
     */
    public static final String WEBHOOK_PATH_SUFFIX = "/webhook";

    // ==================== 超时设置常量 ====================
    
    /**
     * 默认消息删除延迟（秒）
     * 
     * 用于自动删除临时消息，如菜单提示和错误消息。
     */
    public static final int DEFAULT_DELETE_DELAY_SECONDS = 5;
    
    /**
     * 二级菜单删除延迟（秒）
     * 
     * 用于二级菜单的自动删除，比主菜单保留时间更长。
     */
    public static final int SECONDARY_MENU_DELETE_DELAY_SECONDS = 10;
    
    /**
     * 菜单统一删除延迟（秒）
     * 
     * 用于一级和二级菜单的统一自动删除时间，保持用户体验一致性。
     */
    public static final int MENU_DELETE_DELAY_SECONDS = 10;
    
    /**
     * IP 输入超时时间（秒）
     * 
     * 用户输入 IP 地址的超时时间，超过此时间会话将自动清除。
     */
    public static final int IP_INPUT_TIMEOUT_SECONDS = 20;
    
    /**
     * 会话超时时间（秒）
     * 
     * 用户会话的最大存活时间，超过此时间未活动会自动清除。
     */
    public static final int SESSION_TIMEOUT_SECONDS = 300; // 5分钟
    
    /**
     * 消息重试间隔（秒）
     * 
     * 消息发送失败后的重试间隔时间。
     */
    public static final int MESSAGE_RETRY_INTERVAL_SECONDS = 2;

    // ==================== 消息模板常量 ====================
    
    /**
     * 欢迎消息文本
     * 
     * 主菜单的欢迎提示，引导用户选择服务。
     */
//    public static final String WELCOME_MESSAGE = "✨✨✨ 选择服务: 👇👇";
     public static final String WELCOME_MESSAGE = "欢迎使用运维助手。\n" +
            "请选择需要的服务 👇";
    
    /**
     * IP 输入提示消息
     * 
     * 提示用户输入 IP 地址和用户名的格式说明。
     */
    public static final String IP_INPUT_PROMPT = "请提供IP及用户名\n格式为：IP+用户名\n如:1.1.1.1+username";
    
    /**
     * 菜单超时提示模板
     * 
     * 提示用户操作时限的模板，使用 String.format 填充时间。
     * 
     * 使用示例：String.format(MENU_TIMEOUT_TEMPLATE, 30)
     */
    public static final String MENU_TIMEOUT_TEMPLATE = "请在 %d 秒内完成操作：";
    
    /**
     * 操作成功提示
     * 
     * 通用操作成功提示消息。
     */
    public static final String OPERATION_SUCCESS = "✅ 操作成功完成！";
    
    /**
     * 操作取消提示
     * 
     * 用户取消操作时的提示消息。
     */
    public static final String OPERATION_CANCELLED = "❌ 操作已取消。";
    
    /**
     * 返回主菜单按钮文本
     * 
     * 用于二级菜单返回主菜单的按钮文本。
     */
    public static final String BACK_TO_MAIN_MENU = "🔙 返回主菜单";

    // ==================== 错误消息常量 ====================
    
    /**
     * IP 格式解析错误
     * 
     * 用户输入的 IP 格式不正确时的错误提示。
     */
    public static final String IP_PARSE_ERROR = "输入格式错误！请确保格式为：IP+目标用户 (e.g. 1.1.1.1+forward)。";
    
    /**
     * 操作失败提示
     * 
     * 通用操作失败提示，建议用户联系管理员。
     */
    public static final String OPERATION_FAILED = "❌ 加白失败！请联系管理员。";
    
    /**
     * 权限不足提示
     * 
     * 用户权限不足时的提示消息。
     */
    public static final String PERMISSION_DENIED = "❌ 权限不足！无法执行此操作。";
    
    /**
     * 资源未找到提示
     * 
     * 请求的资源未找到时的提示消息。
     */
    public static final String RESOURCE_NOT_FOUND = "❌ 未找到相关资源！";
    
    /**
     * 系统错误提示
     * 
     * 系统内部错误时的通用提示消息。
     */
    public static final String SYSTEM_ERROR = "❌ 系统错误！请稍后重试。";

    // ==================== 限制设置常量 ====================
    
    /**
     * IP 地址最大长度
     * 
     * IP 地址字符串的最大允许长度（IPv6 支持）。
     */
    public static final int IP_MAX_LENGTH = 45;
    
    /**
     * 用户名最大长度
     * 
     * 用户名的最大允许长度。
     */
    public static final int USERNAME_MAX_LENGTH = 64;
    
    /**
     * 消息文本最大长度
     * 
     * Telegram API 支持的消息文本最大长度。
     */
    public static final int MESSAGE_MAX_LENGTH = 4096;
    
    /**
     * 回调数据最大长度
     * 
     * Telegram API 支持的回调数据最大长度（字节）。
     */
    public static final int CALLBACK_DATA_MAX_LENGTH = 64;
    
    /**
     * 按钮文本最大长度
     * 
     * Telegram API 支持的按钮文本最大长度。
     */
    public static final int BUTTON_TEXT_MAX_LENGTH = 64;
    
    /**
     * 内联键盘最大行数
     * 
     * Telegram API 支持的内联键盘最大行数。
     */
    public static final int INLINE_KEYBOARD_MAX_ROWS = 8;
    
    /**
     * 内联键盘每行最大按钮数
     * 
     * Telegram API 支持的内联键盘每行最大按钮数。
     */
    public static final int INLINE_KEYBOARD_MAX_BUTTONS_PER_ROW = 8;

    // ==================== 状态常量 ====================
    
    /**
     * 启用状态值
     * 
     * 表示实体或功能处于启用状态。
     */
    public static final int STATUS_ENABLED = 1;
    
    /**
     * 禁用状态值
     * 
     * 表示实体或功能处于禁用状态。
     */
    public static final int STATUS_DISABLED = 0;
    
    /**
     * 会话状态前缀
     * 
     * 所有会话状态的前缀，用于状态管理和过滤。
     */
    public static final String SESSION_STATE_PREFIX = "AWAITING_";
    
    /**
     * 等待前端 IP 状态
     * 
     * 用户会话状态：等待用户输入前端 IP。
     */
    public static final String SESSION_STATE_AWAITING_FRONTEND_IP = SESSION_STATE_PREFIX + "FRONTEND_IP";
    
    /**
     * 等待后端 IP 状态
     * 
     * 用户会话状态：等待用户输入后端 IP。
     */
    public static final String SESSION_STATE_AWAITING_BACKEND_IP = SESSION_STATE_PREFIX + "BACKEND_IP";
    
    /**
     * 启动请求处理状态
     * 
     * 用户会话状态：正在处理 /start 命令，防止重复点击。
     */
    public static final String SESSION_STATE_PROCESSING_START = SESSION_STATE_PREFIX + "PROCESSING_START";

    // ==================== 回调数据常量 ====================
    
    /**
     * 回调数据前缀
     * 
     * 所有回调数据的前缀，用于回调数据管理和过滤。
     */
    public static final String CALLBACK_PREFIX = "callback_data_";
    
    /**
     * IP 白名单操作回调数据
     * 
     * IP 白名单相关的回调数据标识。
     */
    public static final String CALLBACK_IP_WHITELIST_ACTION = "DOMAIN_WHITELIST_ACTION";
    
    /**
     * 前端域名操作回调数据
     * 
     * 前端域名相关的回调数据标识。
     */
    public static final String CALLBACK_FRONTEND_WEB_DOMAIN_ACTION = "FRONTEND_WEB_DOMAIN_ACTION";
    
    /**
     * 后端域名操作回调数据
     * 
     * 后端域名相关的回调数据标识。
     */
    public static final String CALLBACK_FRONTEND_ADMIN_DOMAIN_ACTION = "FRONTEND_ADMIN_DOMAIN_ACTION";

    public static final String WARNING_TEXT = "⚠️ 您有一个正在进行的操作，请完成当前操作或发送 /new 发起新请求。";

}