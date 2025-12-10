package com.backend.bot.config;

import com.backend.bot.enums.BotType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * BotType 枚举写入转换器
 * 
 * 该类实现了 Spring Data 的 Converter 接口，负责将 Java 的 BotType 枚举类型
 * 转换为数据库中存储的字符串值。这是 R2DBC 响应式数据库访问框架的
 * 类型转换机制的一部分，与 BotTypeReadingConverter 配合使用。
 * 
 * 工作流程：
 * 1. 当向数据库写入 BotType 字段时，Spring Data 会自动调用此转换器
 * 2. 转换器调用枚举的 getDbValue() 方法获取对应的数据库存储值
 * 3. 返回该字符串值供数据库存储
 * 
 * 设计优势：
 * - 实现了 Java 枚举与数据库存储值的解耦
 * - 数据库可以存储更友好的字符串值而非枚举名称
 * - 支持数据库字段的国际化需求
 * - 提高了代码的可维护性和可读性
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@WritingConverter // 标记为从 Java 对象写入到数据库的转换器
public class BotTypeWritingConverter implements Converter<BotType, String> {
    
    /**
     * 执行 BotType 枚举到字符串的转换
     * 
     * 该方法接收 BotType 枚举实例，并将其转换为适合数据库存储的字符串值。
     * 转换逻辑基于 BotType 枚举中定义的 getDbValue() 方法。
     * 
     * 转换过程：
     * 1. 接收 BotType 枚举实例作为输入
     * 2. 调用枚举的 getDbValue() 方法获取对应的数据库存储值
     * 3. 返回该字符串值供 R2DBC 框架写入数据库
     * 
     * 设计考虑：
     * - 直接委托给枚举的 getDbValue() 方法，保持单一职责原则
     * - 不进行空值检查，因为 Spring Data 会在调用前处理空值
     * - 简洁的实现确保了高性能和可靠性
     * 
     * @param source 待写入数据库的 BotType 枚举实例
     * @return 适合数据库存储的字符串值
     */
    @Override
    public String convert(BotType source) {
        // 直接调用枚举的 getDbValue() 方法获取数据库存储值
        // 这种设计将转换逻辑封装在枚举内部，提高了内聚性
        return source.getDbValue();
    }
}

