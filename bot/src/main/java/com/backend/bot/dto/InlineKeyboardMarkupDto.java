package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Telegram 内联键盘标记数据传输对象
 * 
 * 该类是 Telegram 内联键盘的顶级容器，用于组织和排列内联按钮。
 * 内联键盘附加在消息下方，提供交互式菜单和操作选项。
 * 
 * 键盘结构：
 * 1. 键盘由多行按钮组成
 * 2. 每行可以包含多个按钮
 * 3. 按钮在行中水平排列
 * 4. 行与行垂直排列
 * 5. 支持最多 8 行，每行最多 8 个按钮
 * 
 * 应用场景：
 * 1. 交互式菜单导航
 * 2. 多步骤表单和向导
 * 3. 数据筛选和查询
 * 4. 确认对话框
 * 5. 操作选项和设置
 * 
 * 设计特点：
 * 1. 使用嵌套列表结构表示行和列
 * 2. 提供便捷方法添加按钮行
 * 3. 使用 Lombok 简化代码
 * 4. 支持灵活的构造方式
 * 5. 适配 Jackson 序列化
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Getter                      // Lombok 注解，自动生成 getter 方法
@Setter                      // Lombok 注解，自动生成 setter 方法
@NoArgsConstructor           // Lombok 注解，自动生成无参构造函数
// 不使用 @AllArgsConstructor，避免与字段初始化冲突
public class InlineKeyboardMarkupDto {
    
    /**
     * 内联键盘按钮矩阵
     * 
     * 使用嵌套列表结构表示键盘的行和列：
     * - 外层 List 表示键盘的行
     * - 内层 List 表示行中的按钮
     * - 按钮按从左到右、从上到下的顺序排列
     * 
     * 结构示例：
     * [
     *   [button1, button2],  // 第一行
     *   [button3],           // 第二行
     *   [button4, button5]   // 第三行
     * ]
     * 
     * 限制：
     * - 最多 8 行
     * - 每行最多 8 个按钮
     * - 按钮文本最多 64 字符
     * - 回调数据最多 64 字节
     * 
     * 初始化策略：
     * - 使用 ArrayList 确保可变性
     * - 默认初始化为空列表，避免 NPE
     * - 支持动态添加和修改按钮
     * 
     * Telegram API 映射：inline_keyboard
     */
    @JsonProperty("inline_keyboard")
    private List<List<InlineKeyboardButtonDto>> inlineKeyboard = new ArrayList<>();

    /**
     * 带参数的构造函数
     * 
     * 使用指定的按钮列表创建键盘。
     * 
     * @param keyboard 按钮行列表
     */
    public InlineKeyboardMarkupDto(List<List<InlineKeyboardButtonDto>> keyboard) {
        // 如果传入的列表不为空，则创建副本；否则使用空列表
        this.inlineKeyboard = (keyboard != null) ? new ArrayList<>(keyboard) : new ArrayList<>();
    }

    /**
     * 添加按钮行
     * 
     * 向键盘添加一行按钮，按钮在行中从左到右排列。
     * 这是构建键盘的主要方法，提供便捷的行添加功能。
     * 
     * 使用示例：
     * keyboard.addRow(button1, button2); // 添加包含两个按钮的行
     * keyboard.addRow(button3);         // 添加包含一个按钮的行
     * 
     * 参数处理：
     * - 接受可变参数，可以传入任意数量的按钮
     * - 自动处理 null 和空参数
     * - 使用 Arrays.asList 创建按钮列表
     * - 防止添加空行到键盘
     * 
     * 设计考虑：
     * - 方法签名使用可变参数，提供灵活的调用方式
     * - 参数验证确保方法的健壮性
     * - 保持原有列表结构，不修改传入的按钮引用
     * 
     * @param buttons 要添加到行的按钮，可以是一个或多个
     */
    public void addRow(InlineKeyboardButtonDto... buttons) {
        if (buttons != null && buttons.length > 0) {
            // Arrays.asList 创建固定大小的列表，但可以被添加到 ArrayList 中
            // 这样可以保持按钮的原始顺序，同时允许行列表的动态扩展
            this.inlineKeyboard.add(Arrays.asList(buttons));
        }
    }
    
    /**
     * 添加单按钮行
     * 
     * 便捷方法，向键盘添加只包含一个按钮的行。
     * 适用于需要单独突出显示的按钮或控制按钮布局。
     * 
     * 使用场景：
     * - 返回按钮
     * - 确认按钮
     * - 主要操作按钮
     * - 分隔线按钮
     * 
     * @param button 要添加的按钮
     */
    public void addSingleButtonRow(InlineKeyboardButtonDto button) {
        if (button != null) {
            // 创建只包含一个按钮的列表
            List<InlineKeyboardButtonDto> row = new ArrayList<>();
            row.add(button);
            this.inlineKeyboard.add(row);
        }
    }
    
    /**
     * 获取按钮总数
     * 
     * 计算键盘中所有行的按钮总数。
     * 
     * @return 按钮总数
     */
    public int getButtonCount() {
        int count = 0;
        for (List<InlineKeyboardButtonDto> row : inlineKeyboard) {
            if (row != null) {
                count += row.size();
            }
        }
        return count;
    }
    
    /**
     * 获取行数
     * 
     * 获取键盘中的行数。
     * 
     * @return 行数
     */
    public int getRowCount() {
        return inlineKeyboard != null ? inlineKeyboard.size() : 0;
    }
    
    /**
     * 检查键盘是否为空
     * 
     * 判断键盘是否没有任何按钮行。
     * 
     * @return 如果键盘为空返回 true，否则返回 false
     */
    public boolean isEmpty() {
        return inlineKeyboard == null || inlineKeyboard.isEmpty();
    }
    
    /**
     * 清空键盘
     * 
     * 移除键盘中的所有按钮行。
     */
    public void clear() {
        if (inlineKeyboard != null) {
            inlineKeyboard.clear();
        }
    }
}