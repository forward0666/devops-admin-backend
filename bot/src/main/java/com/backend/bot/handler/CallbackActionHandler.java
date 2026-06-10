package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import reactor.core.publisher.Mono;

/**
 * 通用的回调动作处理器接口。
 * 实现该接口的类负责处理某一类特定的 CallbackQuery 业务逻辑。
 * 采用了职责链（Chain of Responsibility）或策略（Strategy）模式，通过 supports() 方法进行筛选。
 */
public interface CallbackActionHandler {

    /**
     * 检查当前处理器是否支持处理指定的回调数据。
     *
     * @param callbackData 用户点击的按钮数据
     * @return 如果支持，返回 true
     */
    boolean supports(String callbackData);

    /**
     * 检查是否支持处理（含 bot 上下文）。默认实现等同 supports(callbackData)。
     * Handler 可重写此方法做更精细的判断。
     */
    default boolean supports(String callbackData, BotConfigEntity botEntity) {
        return supports(callbackData);
 }

    /**
     * 执行具体的业务逻辑。
     *
     * @param botEntity Bot配置信息
     * @param botUpdate Telegram更新数据
     * @return 执行结果的 Mono<Void>
     */
    Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate);

    /**
     * 定义处理器的执行顺序。数值越小，优先级越高。
     * 菜单导航通常应优先于最终操作。
     * @return 顺序值
     */
    int getOrder();
}