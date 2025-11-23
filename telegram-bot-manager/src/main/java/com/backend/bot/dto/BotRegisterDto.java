package com.backend.bot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BotRegisterDto {
    @NotBlank(message = "botUsername 不能为空")
    private String botUsername;

    @NotBlank(message = "Token 不能为空")
    private String token;

    @NotBlank(message = "botName 不能为空")
    private String botName;

    @NotBlank(message = "Secret token 不能为空")
    private String secretToken;
}
