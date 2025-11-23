package com.backend.bot.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class SetWebhookDto {

    @NotBlank(message = "Bot name is required")
    private String botName;

    @NotBlank(message = "URL is required")
    private String url;

     @NotBlank(message = "Secret token is required")
    private String secretToken;
}