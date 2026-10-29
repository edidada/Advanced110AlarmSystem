package com.alarm.infrastructure.persistence;

import com.alarm.application.port.AuditRecordRepository;
import com.alarm.domain.model.AuditRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class AuditRecordRepositoryImpl implements AuditRecordRepository {

    private static final RowMapper<AuditRecord> MAPPER = (rs, i) -> new AuditRecord(
            rs.getString("id"), rs.getString("actor_id"), rs.getString("action"),
            rs.getString("target_type"), rs.getString("target_id"), rs.getString("detail"),
            toInstant(rs.getTimestamp("occurred_at")));

    private final JdbcTemplate jdbc;

    public AuditRecordRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(AuditRecord record) {
        jdbc.update("""
                        INSERT INTO audit_record (id, actor_id, action, target_type, target_id, detail, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                record.getId(), record.getActorId(), record.getAction(), record.getTargetType(),
                record.getTargetId(), record.getDetail(), Timestamp.from(record.getOccurredAt()));
    }

    @Override
    public List<AuditRecord> listByTarget(String targetType, String targetId) {
        return jdbc.query("""
                        SELECT * FROM audit_record WHERE target_type = ? AND target_id = ?
                        ORDER BY occurred_at
                        """,
                MAPPER, targetType, targetId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
