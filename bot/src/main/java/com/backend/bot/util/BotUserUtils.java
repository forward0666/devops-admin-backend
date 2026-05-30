package com.backend.bot.util;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.Optional;

@UtilityClass
public class BotUserUtils {

    // ----------------------------------------------------
    // --- 修复部分：用于从 DTO 提取 User 对象和 Operator Name ---
    // ----------------------------------------------------

    /**
     * 安全地从 BotUpdateDto 中提取 User 对象。
     * 支持从 message 和 callbackQuery 中提取。
     */
    public static Optional<UserDto> extractUser(BotUpdateDto update) {
        if (update.message() != null && update.message().from() != null) {
            return Optional.of(update.message().from());
        }
        if (update.callbackQuery() != null && update.callbackQuery().from() != null) {
            return Optional.of(update.callbackQuery().from());
        }
        return Optional.empty();
    }

    /**
     * 格式化操作人名称 (firstName + (@username) 或 userId)。
     */
    public static String getOperatorName(UserDto user, Long userId) {
        if (user == null) {
            return "UnknownUser:" + userId; // 安全后备
        }

        String userFirstName = user.firstName();
        String userUsername = user.username();
        String operatorName;

        if (StringUtils.hasText(userFirstName)) {
            // 优先使用 firstName
            operatorName = userFirstName;
            if (StringUtils.hasText(userUsername)) {
                // 如果 username 存在，进行组合：firstName (@username)
                operatorName += " (@" + userUsername + ")";
            }
        } else if (StringUtils.hasText(userUsername)) {
            // 如果 firstName 缺失，仅使用 username
            operatorName = "@" + userUsername;
        } else {
            // 如果两者都没有，使用 userId 作为后备
            operatorName = String.valueOf(userId);
        }

        return operatorName;
    }

    // ----------------------------------------------------
    // --- 原有的日志格式化部分 (保留) ---
    // ----------------------------------------------------

    /**
     * 从 HandlerContext 中提取标准化的用户身份日志字符串
     */
    public static String formatIdentityLog(HandlerContext context) {
        return formatIdentityLog(
                context.userId(),
                context.firstName(),
                context.chatId(),
                context.chatTitle()
        );
    }

    // ... (formatIdentityLog, formatUserPart, formatChatPart 保持不变)

    public static String formatIdentityLog(Long userId, String firstName, Long chatId, String chatTitle) {
        String userPart = formatUserPart(userId, firstName);
        String chatPart = formatChatPart(chatId, chatTitle);
        return String.format("%s %s", userPart, chatPart);
    }

    private static String formatUserPart(Long userId, String firstName) {
        if (StringUtils.hasText(firstName)) {
            return String.format("[User: %d (%s)]", userId, firstName);
        }
        return String.format("[User: %d]", userId);
    }

    private static String formatChatPart(Long chatId, String chatTitle) {
        // 如果是私聊，chatId 通常等于 userId，且没有 title
        if (chatId == null) {
            return "[Chat: N/A]";
        }
        if (StringUtils.hasText(chatTitle)) {
            return String.format("[Chat: %d (%s)]", chatId, chatTitle);
        }
        return String.format("[Chat: %d]", chatId);
    }
}