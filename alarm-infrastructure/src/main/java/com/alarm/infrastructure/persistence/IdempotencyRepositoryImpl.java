package com.alarm.infrastructure.persistence;

import com.alarm.application.port.IdempotencyRepository;
import com.alarm.domain.model.IdempotencyRecord;
import com.alarm.domain.model.IdempotencyState;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class IdempotencyRepositoryImpl implements IdempotencyRepository {

    private static final RowMapper<IdempotencyRecord> MAPPER = (rs, i) -> new IdempotencyRecord(
            rs.getString("scope"), rs.getString("key"), rs.getString("operation"),
            rs.getString("request_hash"), rs.getString("response_body"), toInt(rs.getObject("response_status")),
            IdempotencyState.valueOf(rs.getString("state")), toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public IdempotencyRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> find(String scope, String key) {
        return jdbc.query("SELECT * FROM idempotency_record WHERE scope = ? AND \"key\" = ?",
                MAPPER, scope, key).stream().findFirst();
    }

    @Override
    public boolean tryInsertReserved(IdempotencyRecord record) {
        try {
            jdbc.update("INSERT INTO idempotency_record (scope, \"key\", operation, request_hash, response_body, response_status, state, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    record.getScope(), record.getKey(), record.getOperation(), record.getRequestHash(),
                    record.getResponseBody(), record.getResponseStatus(), record.getState().name(),
                    Timestamp.from(record.getCreatedAt()));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public void markCompleted(String scope, String key, int responseStatus, String responseBody, Instant at) {
        jdbc.update("""
                        UPDATE idempotency_record SET state = 'COMPLETED', response_status = ?,
                            response_body = ? WHERE scope = ? AND "key" = ?
                        """,
                responseStatus, responseBody, scope, key);
    }

    @Override
    public long countByState(IdempotencyState state) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE state = ?",
                Long.class, state.name());
        return count == null ? 0 : count;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Integer toInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
