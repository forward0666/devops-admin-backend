package com.backend.bot.service;

import com.backend.bot.entity.UserSessionEntity;
import com.backend.bot.util.LogUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class RedisUserSessionService implements UserSessionService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentMap<Long, Disposable> pendingDeletions = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Disposable> systemMessageDeletions = new ConcurrentHashMap<>();
    private final AtomicLong systemMessageCounter = new AtomicLong(0);

    private static final String SESSION_KEY_PREFIX = "telegram:session:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(5);

    private String buildSessionKey(Long userId) {
        return SESSION_KEY_PREFIX + userId;
    }

    @Override
    public Mono<Void> updateUserSession(Long userId, String newState, Long referenceMessageId, String botName) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);

            try {
                UserSessionEntity session = UserSessionEntity.builder()
                        .userId(userId)
                        .currentState(newState)
                        .referenceMessageId(referenceMessageId)
                        .botName(botName)
                        .build();

                String sessionKey = buildSessionKey(userId);
                String sessionJson = objectMapper.writeValueAsString(session);

                return redisTemplate.opsForValue()
                        .set(sessionKey, sessionJson, SESSION_TTL)
                        .doOnSuccess(v -> log.info("{}✅ Session stored for userId={}, botName={}, state={}", logPrefix, userId, botName, newState))
                        .then()
                        .onErrorResume(e -> {
                            log.error("{}❌ Failed to store user session in Redis for userId: {}. Error: {}", logPrefix, userId, e.getMessage(), e);
                            return Mono.empty();
                        });
            } catch (JsonProcessingException e) {
                log.error("{}❌ Failed to serialize user session for userId: {}. Error: {}", logPrefix, userId, e.getMessage(), e);
                return Mono.empty();
            }
        });
    }

    @Override
    public Mono<UserSessionEntity> getUserSession(Long userId) {
        return doGetSession(userId, null);
    }

    @Override
    public Mono<UserSessionEntity> getUserSession(Long userId, String botName) {
        return doGetSession(userId, botName);
    }

    private Mono<UserSessionEntity> doGetSession(Long userId, String botName) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);
            String sessionKey = buildSessionKey(userId);

            return redisTemplate.opsForValue().get(sessionKey)
                    .doOnNext(json -> log.info("{}🔎 Raw session JSON for userId={}, botName={}: {}", logPrefix, userId, botName, json))
                    .flatMap(sessionJson -> {
                        try {
                            UserSessionEntity session = objectMapper.readValue(sessionJson, UserSessionEntity.class);
                            // 按 botName 过滤
                            if (botName != null && !botName.equals(session.getBotName())) {
                                log.debug("{}🔎 Session belongs to bot {} (expected {}), skipping for userId: {}", logPrefix, session.getBotName(), botName, userId);
                                return Mono.empty();
                            }
                            log.debug("{}🔎 Found session in Redis for userId: {}. State: {}", logPrefix, userId, session.getCurrentState());
                            return Mono.just(session);
                        } catch (JsonProcessingException e) {
                            log.error("{}❌ Failed to deserialize user session from Redis for userId: {}. Error: {}", logPrefix, userId, e.getMessage(), e);
                            clearUserSession(userId).contextWrite(Context.of(contextView)).subscribe();
                            return Mono.empty();
                        }
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.debug("{}🔎 No session found in Redis for userId: {}", logPrefix, userId);
                        return Mono.empty();
                    }));
        });
    }

    @Override
    public Mono<Boolean> hasAnySession(Long userId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);
            String sessionKey = buildSessionKey(userId);

            return redisTemplate.hasKey(sessionKey)
                    .defaultIfEmpty(false)
                    .onErrorResume(e -> {
                        log.error("{}❌ Failed to check session key for userId: {}. Error: {}", logPrefix, userId, e.getMessage(), e);
                        return Mono.just(false);
                    });
        });
    }

    @Override
    public Mono<Boolean> hasAnySession(Long userId, String botName) {
        return getUserSession(userId, botName).hasElement();
    }

    @Override
    public Mono<Void> clearUserSession(Long userId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);
            String sessionKey = buildSessionKey(userId);

            return redisTemplate.delete(sessionKey)
                    .then()
                    .onErrorResume(e -> {
                        log.error("{}❌ Failed to clear user session from Redis for userId: {}. Error: {}", logPrefix, userId, e.getMessage(), e);
                        return Mono.empty();
                    });
        });
    }

    @Override
    public Mono<Void> storePendingDeletion(Long userId, Disposable deletionTask) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);

            if (userId == 0L) {
                long uniqueId = systemMessageCounter.incrementAndGet();
                systemMessageDeletions.put(uniqueId, deletionTask);
                return Mono.empty();
            } else {
                return cancelPendingDeletion(userId)
                        .then(Mono.fromRunnable(() -> {
                            pendingDeletions.put(userId, deletionTask);
                            log.debug("{}⏳ Stored new deletion task in memory for userId: {}", logPrefix, userId);
                        }));
            }
        });
    }

    @Override
    public Mono<Void> cancelPendingDeletion(Long userId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);

            if (userId == 0L) {
                systemMessageDeletions.forEach((id, disposable) -> {
                    if (disposable != null && !disposable.isDisposed()) {
                        disposable.dispose();
                        log.debug("{}✅ Cancelled system message deletion task for uniqueId: {}", logPrefix, id);
                    }
                });
                systemMessageDeletions.clear();
                return Mono.empty();
            } else {
                return Mono.fromRunnable(() -> {
                    Disposable disposable = pendingDeletions.remove(userId);
                    if (disposable != null && !disposable.isDisposed()) {
                        disposable.dispose();
                    }
                });
            }
        });
    }

    private String getLogPrefix(ContextView contextView) {
        try {
            return LogUtils.prepareMdcAndGetPrefix(contextView);
        } catch (Exception e) {
            return "[traceId=N/A]";
        }
    }
}
