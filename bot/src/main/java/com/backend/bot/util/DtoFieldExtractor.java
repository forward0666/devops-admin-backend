package com.backend.bot.util;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.CallbackQueryDto;
import com.backend.bot.dto.ChatDto;
import com.backend.bot.dto.MessageDto;
import com.backend.bot.dto.UserDto;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * DTO 字段提取工具类
 * 
 * 该类提供统一的 DTO 字段提取方法，遵循 DRY 原则，
 * 减少重复的字段访问和空值检查代码。使用 Stream API
 * 提供类型安全和函数式的字段提取方式。
 * 
 * 主要功能：
 * 1. 安全提取更新中的用户信息
 * 2. 安全提取更新中的聊天信息
 * 3. 统一格式化用户显示名称
 * 4. 提供类型安全的字段访问
 * 5. 简化空值检查和默认值处理
 * 
 * 设计模式：
 * 1. 工具类模式 - 提供静态方法
 * 2. Optional 模式 - 安全处理可能为空的字段
 * 3. Stream 模式 - 简化多源字段提取
 * 4. 函数式编程 - 使用方法引用和 Lambda
 * 
 * @author Backend Team
 * @version 1.0.0
 */
public final class DtoFieldExtractor {

    /**
     * 私有构造函数，防止工具类实例化
     */
    private DtoFieldExtractor() {}

    /**
     * 从更新对象中提取用户信息
     * 
     * 安全地从 BotUpdateDto 中提取 UserDto 对象，支持从 message 和 callbackQuery 中提取。
     * 使用 Stream API 处理多个可能的用户源，提高代码可读性和可维护性。
     * 
     * 提取优先级：
     * 1. Message.from - 消息的发送者
     * 2. CallbackQuery.from - 回调查询的触发者
     * 
     * @param update Telegram 更新对象
     * @return 包含 UserDto 的 Optional，如果找不到则为 Optional.empty()
     */
    public static Optional<UserDto> extractUserFromUpdate(BotUpdateDto update) {
        return Stream.of(
                Optional.ofNullable(update.message()).map(MessageDto::from),
                Optional.ofNullable(update.callbackQuery()).map(CallbackQueryDto::from)
            )
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    /**
     * 从更新对象中提取聊天信息
     * 
     * 安全地从 BotUpdateDto 中提取 ChatDto 对象，支持从 message 和 callbackQuery 中提取。
     * 使用 Stream API 处理多个可能的聊天源，提高代码可读性和可维护性。
     * 
     * 提取优先级：
     * 1. Message.chat - 消息所属的聊天
     * 2. CallbackQuery.message.chat - 回调查询所属消息的聊天
     * 
     * @param update Telegram 更新对象
     * @return 包含 ChatDto 的 Optional，如果找不到则为 Optional.empty()
     */
    public static Optional<ChatDto> extractChatFromUpdate(BotUpdateDto update) {
        return Stream.of(
                Optional.ofNullable(update.message()).map(MessageDto::chat),
                Optional.ofNullable(update.callbackQuery())
                    .map(CallbackQueryDto::message)
                    .map(MessageDto::chat)
            )
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    /**
     * 从更新对象中提取聊天 ID
     * 
     * 安全地从 BotUpdateDto 中提取聊天 ID，支持从 message 和 callbackQuery 中提取。
     * 使用 Stream API 处理多个可能的 ID 源，提高代码可读性和可维护性。
     * 
     * 提取优先级：
     * 1. Message.chat.id - 消息所属聊天的 ID
     * 2. CallbackQuery.message.chat.id - 回调查询所属消息聊天的 ID
     * 
     * @param update Telegram 更新对象
     * @return 包含聊天 ID 的 Optional，如果找不到则为 Optional.empty()
     */
    public static Optional<Long> extractChatIdFromUpdate(BotUpdateDto update) {
        return Stream.of(
                Optional.ofNullable(update.message())
                    .map(MessageDto::chat)
                    .map(ChatDto::id),
                Optional.ofNullable(update.callbackQuery())
                    .map(CallbackQueryDto::message)
                    .map(MessageDto::chat)
                    .map(ChatDto::id)
            )
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    /**
     * 从更新对象中提取用户 ID
     * 
     * 安全地从 BotUpdateDto 中提取用户 ID，支持从 message 和 callbackQuery 中提取。
     * 使用 Stream API 处理多个可能的 ID 源，提高代码可读性和可维护性。
     * 
     * 提取优先级：
     * 1. Message.from.id - 消息发送者的 ID
     * 2. CallbackQuery.from.id - 回调查询触发者的 ID
     * 
     * @param update Telegram 更新对象
     * @return 包含用户 ID 的 Optional，如果找不到则为 Optional.empty()
     */
    public static Optional<Long> extractUserIdFromUpdate(BotUpdateDto update) {
        return Stream.of(
                Optional.ofNullable(update.message())
                    .map(MessageDto::from)
                    .map(UserDto::id),
                Optional.ofNullable(update.callbackQuery())
                    .map(CallbackQueryDto::from)
                    .map(UserDto::id)
            )
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    /**
     * 从更新对象中提取消息 ID
     * 
     * 安全地从 BotUpdateDto 中提取消息 ID，支持从 message 和 callbackQuery 中提取。
     * 使用 Stream API 处理多个可能的 ID 源，提高代码可读性和可维护性。
     * 
     * 提取优先级：
     * 1. Message.messageId - 消息 ID
     * 2. CallbackQuery.message.messageId - 回调查询所属消息的 ID
     * 
     * @param update Telegram 更新对象
     * @return 包含消息 ID 的 Optional，如果找不到则为 Optional.empty()
     */
    public static Optional<Long> extractMessageIdFromUpdate(BotUpdateDto update) {
        return Stream.of(
                Optional.ofNullable(update.message())
                    .map(MessageDto::messageId),
                Optional.ofNullable(update.callbackQuery())
                    .map(CallbackQueryDto::message)
                    .map(MessageDto::messageId)
            )
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    /**
     * 从更新对象中提取消息文本
     * 
     * 安全地从 BotUpdateDto 中提取消息文本，支持从 message 中提取。
     * 对于 callbackQuery，返回 null，因为回调查询通常没有文本内容。
     * 
     * @param update Telegram 更新对象
     * @return 包含消息文本的 Optional，如果找不到则为 Optional.empty()
     */
    public static Optional<String> extractMessageTextFromUpdate(BotUpdateDto update) {
        return Optional.ofNullable(update.message())
            .map(MessageDto::text);
    }

    /**
     * 从更新对象中提取回调数据
     * 
     * 安全地从 BotUpdateDto 中提取回调数据，支持从 callbackQuery 中提取。
     * 对于普通消息，返回 null，因为消息没有回调数据。
     * 
     * @param update Telegram 更新对象
     * @return 包含回调数据的 Optional，如果找不到则为 Optional.empty()
     */
    public static Optional<String> extractCallbackDataFromUpdate(BotUpdateDto update) {
        return Optional.ofNullable(update.callbackQuery())
            .map(CallbackQueryDto::data);
    }

    /**
     * 从用户对象中提取显示名称
     * 
     * 根据 UserDto 对象生成适合显示的用户名称，优先使用 firstName，
     * 如果不可用则使用 username，最后回退到用户 ID。
     * 
     * 格式规则：
     * 1. 如果有 firstName 和 username：firstName (@username)
     * 2. 如果只有 firstName：firstName
     * 3. 如果只有 username：@username
     * 4. 如果都没有：userId
     * 
     * @param user 用户 DTO 对象
     * @param userId 用户 ID（作为后备显示名称）
     * @return 格式化的用户显示名称
     */
    public static String formatDisplayName(UserDto user, Long userId) {
        if (user == null) {
            return String.valueOf(userId);
        }

        String firstName = user.firstName();
        String username = user.username();

        if (StringUtils.hasText(firstName)) {
            // 优先使用 firstName
            if (StringUtils.hasText(username)) {
                // 如果 username 存在，进行组合：firstName (@username)
                return String.format("%s (@%s)", firstName, username);
            }
            return firstName;
        } else if (StringUtils.hasText(username)) {
            // 如果 firstName 缺失，仅使用 username
            return String.format("@%s", username);
        } else {
            // 如果两者都没有，使用 userId 作为后备
            return String.valueOf(userId);
        }
    }

    /**
     * 从用户对象中提取短显示名称
     * 
     * 生成适合在空间受限场景下显示的短用户名称。
     * 优先使用 firstName，如果不可用则使用 username，最后回退到用户 ID。
     * 
     * 格式规则：
     * 1. 如果有 firstName：firstName
     * 2. 如果只有 username：@username
     * 3. 如果都没有：userId
     * 
     * @param user 用户 DTO 对象
     * @param userId 用户 ID（作为后备显示名称）
     * @return 格式化的短用户显示名称
     */
    public static String formatShortDisplayName(UserDto user, Long userId) {
        if (user == null) {
            return String.valueOf(userId);
        }

        String firstName = user.firstName();
        String username = user.username();

        if (StringUtils.hasText(firstName)) {
            return firstName;
        } else if (StringUtils.hasText(username)) {
            return String.format("@%s", username);
        } else {
            return String.valueOf(userId);
        }
    }

    /**
     * 从聊天对象中提取显示名称
     * 
     * 根据 ChatDto 对象生成适合显示的聊天名称，优先使用 title，
     * 如果不可用则使用 username，最后回退到聊天 ID。
     * 
     * 格式规则：
     * 1. 如果有 title：title
     * 2. 如果只有 username：@username
     * 3. 如果都没有：Chat: chatId
     * 
     * @param chat 聊天 DTO 对象
     * @return 格式化的聊天显示名称
     */
    public static String formatDisplayName(ChatDto chat) {
        if (chat == null) {
            return "Unknown Chat";
        }

        Long chatId = chat.id();
        String title = chat.title();
        String username = chat.username();

        if (StringUtils.hasText(title)) {
            return title;
        } else if (StringUtils.hasText(username)) {
            return String.format("@%s", username);
        } else {
            return String.format("Chat: %d", chatId);
        }
    }

    /**
     * 从聊天对象中提取公共链接
     * 
     * 生成聊天的 Telegram 公共链接，仅适用于有 username 的聊天。
     * 
     * @param chat 聊天 DTO 对象
     * @return 公共链接 URL，如果没有 username 则返回 null
     */
    public static String formatPublicLink(ChatDto chat) {
        if (chat == null || !StringUtils.hasText(chat.username())) {
            return null;
        }

        return String.format("https://t.me/%s", chat.username());
    }

    /**
     * 从用户对象中提取公共链接
     * 
     * 生成用户的 Telegram 公共链接，仅适用于有 username 的用户。
     * 
     * @param user 用户 DTO 对象
     * @return 公共链接 URL，如果没有 username 则返回 null
     */
    public static String formatPublicLink(UserDto user) {
        if (user == null || !StringUtils.hasText(user.username())) {
            return null;
        }

        return String.format("https://t.me/%s", user.username());
    }
}