package com.alarm.application.support;

import com.alarm.application.port.Clock;
import com.alarm.application.port.IdempotencyRepository;
import com.alarm.domain.model.IdempotencyRecord;
import com.alarm.domain.model.IdempotencyState;
import com.alarm.domain.exception.IdempotencyConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 幂等执行器：幂等记录与业务写入同一事务，提交后即具备去重能力。
 * - 同 scope+key 同请求摘要且 COMPLETED：返回缓存响应。
 * - 同 key 不同摘要：409 ALM-2102。
 * - 并发唯一键冲突：由仓储返回 false 后读取既有记录判定。
 * 调用方必须处于事务内（@Transactional）。
 */
@Component
public class IdempotencyExecutor {

    private final IdempotencyRepository repository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public IdempotencyExecutor(IdempotencyRepository repository, Clock clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public <T> T execute(String scope, String key, String operation, Object request,
                         Class<T> responseType, Supplier<T> action) {
        if (scope == null || scope.isBlank() || key == null || key.isBlank()) {
            throw new IdempotencyConflictException("idempotency scope and key are required");
        }
        String hash = hashRequest(request);
        IdempotencyRecord existing = repository.find(scope, key).orElse(null);
        if (existing == null) {
            boolean inserted = repository.tryInsertReserved(new IdempotencyRecord(
                    scope, key, operation, hash, null, null, IdempotencyState.RESERVED, clock.now()));
            if (!inserted) {
                existing = repository.find(scope, key)
                        .orElseThrow(() -> new IdempotencyConflictException("idempotency key race, retry request"));
            }
        }
        if (existing != null) {
            if (!existing.getRequestHash().equals(hash)) {
                throw new IdempotencyConflictException("idempotency key already used with a different request body");
            }
            if (existing.getState() == IdempotencyState.COMPLETED && existing.getResponseBody() != null) {
                return deserialize(existing.getResponseBody(), responseType);
            }
            // RESERVED 由并发事务持有 -> 409
            throw new IdempotencyConflictException("idempotent request in progress for scope=" + scope);
        }
        T result = action.get();
        repository.markCompleted(scope, key, 200, serialize(result), clock.now());
        return result;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("serialize idempotency response failed", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("deserialize idempotency response failed", e);
        }
    }

    private String hashRequest(Object request) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(request == null ? "" : request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        } catch (Exception e) {
            throw new IllegalStateException("hash request failed", e);
        }
    }
}
