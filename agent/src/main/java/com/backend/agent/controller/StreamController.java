package com.backend.agent.controller;

import com.backend.agent.service.StreamService;
import com.backend.utils.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE streaming chat endpoints.
 * Matches agent-bak/app/routes/stream.py surface.
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;

    /**
     * SSE streaming chat endpoint.
     * GET /agent/{id}/chat/stream?message=xxx&sessionId=xxx
     */
    @GetMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable Integer id,
                                 @RequestParam String message,
                                 @RequestParam(defaultValue = "default") String sessionId) {
        log.info("SSE stream: agent={}, session={}, msg='{}'", id, sessionId, message.substring(0, Math.min(message.length(), 50)));
        SseEmitter emitter = new SseEmitter(300_000L);
        streamService.streamChat(id, message, sessionId, emitter);
        return emitter;
    }
}