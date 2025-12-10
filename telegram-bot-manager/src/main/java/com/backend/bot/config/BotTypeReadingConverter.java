package com.backend.bot.config;

import com.backend.bot.enums.BotType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * BotType 枚举读取转换器
 * 
 * 该类实现了 Spring Data 的 Converter 接口，负责将数据库中存储的字符串值
 * 转换为 Java 的 BotType 枚举类型。这是 R2DBC 响应式数据库访问框架的
 * 类型转换机制的一部分。
 * 
 * 工作流程：
 * 1. 当从数据库读取 BotType 字段时，Spring Data 会自动调用此转换器
 * 2. 转换器遍历 BotType 枚举的所有值，查找匹配的数据库值
 * 3. 返回对应的枚举实例，如果找不到则抛出异常
 * 
 * 设计考虑：
 * - 使用 @ReadingConverter 注解标记为读取转换器
 * - 实现 Converter<String, BotType> 接口，明确转换方向
 * - 采用严格匹配策略，不匹配的值会抛出异常而非返回默认值
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@ReadingConverter // 标记为从数据库读取到 Java 对象的转换器
public class BotTypeReadingConverter implements Converter<String, BotType> {
    
    /**
     * 执行字符串到 BotType 枚举的转换
     * 
     * 该方法接收数据库中存储的字符串值，并将其转换为对应的 BotType 枚举。
     * 转换逻辑基于 BotType 枚举中定义的 getDbValue() 方法返回的数据库值。
     * 
     * 转换过程：
     * 1. 遍历 BotType 枚举的所有可能值
     * 2. 比较每个枚举的 getDbValue() 返回值与输入字符串
     * 3. 返回第一个匹配的枚举实例
     * 4. 如果没有找到匹配项，抛出 IllegalArgumentException
     * 
     * 异常处理：
     * 采用快速失败策略，当输入值无法匹配任何枚举时直接抛出异常。
     * 这种设计有助于及早发现数据一致性问题，避免使用默认值掩盖错误。
     * 
     * @param source 从数据库读取的字符串值，需要转换为枚举
     * @return 对应的 BotType 枚举实例
     * @throws IllegalArgumentException 当输入值无法匹配任何已知枚举时抛出
     */
    @Override
    public BotType convert(String source) {
        // 遍历 BotType 枚举的所有值，寻找匹配的数据库值
        for (BotType type : BotType.values()) {
            // 比较枚举的数据库值与输入字符串
            if (type.getDbValue().equals(source)) {
                return type;
            }
        }
        
        // 未找到匹配的枚举值，抛出异常
        // 这里采用快速失败策略，而不是返回默认值，有助于及早发现数据问题
        throw new IllegalArgumentException("Unknown BotType value: " + source);
    }
}
