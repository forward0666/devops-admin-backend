package com.backend.agent.dto;

import lombok.Data;

@Data
public class AgentChatRequest {
    private String message;
    private String sessionId;
}