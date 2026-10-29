package com.alarm.infrastructure.persistence;

import com.alarm.application.port.OutboxRepository;
import com.alarm.domain.model.OutboxEvent;
import com.alarm.domain.model.OutboxState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class OutboxRepositoryImpl implements OutboxRepository {

    private static final RowMapper<OutboxEvent> MAPPER = (rs, i) -> {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(rs.getString("event_id"));
        event.setEventType(rs.getString("event_type"));
        event.setIncidentId(rs.getString("incident_id"));
        event.setAggregateType(rs.getString("aggregate_type"));
        event.setAggregateId(rs.getString("aggregate_id"));
        event.setAggregateVersion(rs.getLong("aggregate_version"));
        event.setPayload(rs.getString("payload"));
        event.setTraceId(rs.getString("trace_id"));
        event.setState(OutboxState.valueOf(rs.getString("state")));
        event.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        event.setPublishedAt(toInstant(rs.getTimestamp("published_at")));
        return event;
    };

    private final JdbcTemplate jdbc;

    public OutboxRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(OutboxEvent event) {
        jdbc.update("""
                        INSERT INTO outbox_event (event_id, event_type, incident_id, aggregate_type,
                            aggregate_id, aggregate_version, payload, trace_id, state, created_at, published_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                event.getEventId(), event.getEventType(), event.getIncidentId(),
                event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(),
                event.getPayload(), event.getTraceId(), event.getState().name(),
                Timestamp.from(event.getCreatedAt()), ts(event.getPublishedAt()));
    }

    @Override
    public List<OutboxEvent> findPendingBatch(int limit) {
        return jdbc.query(
                "SELECT * FROM outbox_event WHERE state = 'PENDING' ORDER BY created_at LIMIT ?",
                MAPPER, limit);
    }

    @Override
    public void markPublished(String eventId, Instant publishedAt) {
        jdbc.update("UPDATE outbox_event SET state = 'PUBLISHED', published_at = ? WHERE event_id = ?",
                Timestamp.from(publishedAt), eventId);
    }

    @Override
    public long countByState(OutboxState state) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE state = ?",
                Long.class, state.name());
        return count == null ? 0 : count;
    }

    @Override
    public List<OutboxEvent> listByAggregateId(String aggregateId) {
        return jdbc.query("SELECT * FROM outbox_event WHERE aggregate_id = ? ORDER BY created_at, event_id",
                MAPPER, aggregateId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
