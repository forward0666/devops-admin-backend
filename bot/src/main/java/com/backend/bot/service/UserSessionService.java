package com.backend.bot.service;

import com.backend.bot.entity.UserSessionEntity;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

public interface UserSessionService {

    Mono<Void> updateUserSession(Long userId, String newState, Long referenceMessageId, String botName);

    Mono<UserSessionEntity> getUserSession(Long userId);

    Mono<UserSessionEntity> getUserSession(Long userId, String botName);

    Mono<Void> clearUserSession(Long userId);

    Mono<Boolean> hasAnySession(Long userId);

    Mono<Boolean> hasAnySession(Long userId, String botName);

    Mono<Void> storePendingDeletion(Long userId, Disposable deletionTask);

    Mono<Void> cancelPendingDeletion(Long userId);
}
