package com.alarm.infrastructure.persistence;

import com.alarm.application.port.DispatchTaskRepository;
import com.alarm.contracts.enums.DispatchStatus;
import com.alarm.domain.model.DispatchTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class DispatchTaskRepositoryImpl implements DispatchTaskRepository {

    private static final String ACTIVE_SET = "('PENDING_CONFIRM','ACCEPTED','ENROUTE','ARRIVED')";

    private static final RowMapper<DispatchTask> MAPPER = (rs, i) -> {
        DispatchTask task = new DispatchTask();
        task.setId(rs.getString("id"));
        task.setIncidentId(rs.getString("incident_id"));
        task.setUnitId(rs.getString("unit_id"));
        task.setCommandId(rs.getString("command_id"));
        task.setStatus(DispatchStatus.valueOf(rs.getString("status")));
        task.setNote(rs.getString("note"));
        task.setAssignedBy(rs.getString("assigned_by"));
        task.setDeadline(toInstant(rs.getTimestamp("deadline")));
        task.setVersion(rs.getLong("version"));
        task.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        task.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return task;
    };

    private final JdbcTemplate jdbc;

    public DispatchTaskRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DispatchTask> findById(String id) {
        return jdbc.query("SELECT * FROM dispatch_task WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public List<DispatchTask> listByIncidentId(String incidentId) {
        return jdbc.query(
                "SELECT * FROM dispatch_task WHERE incident_id = ? ORDER BY created_at", MAPPER, incidentId);
    }

    @Override
    public int countActiveByIncidentId(String incidentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dispatch_task WHERE incident_id = ? AND status IN " + ACTIVE_SET,
                Integer.class, incidentId);
        return count == null ? 0 : count;
    }

    @Override
    public boolean existsActiveByIncidentAndUnit(String incidentId, String unitId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dispatch_task WHERE incident_id = ? AND unit_id = ? AND status IN " + ACTIVE_SET,
                Integer.class, incidentId, unitId);
        return count != null && count > 0;
    }

    @Override
    public List<DispatchTask> findExpiredPending(Instant now) {
        return jdbc.query(
                "SELECT * FROM dispatch_task WHERE status = 'PENDING_CONFIRM' AND deadline < ? ORDER BY deadline",
                MAPPER, Timestamp.from(now));
    }

    @Override
    public void insert(DispatchTask task) {
        jdbc.update("""
                        INSERT INTO dispatch_task (id, incident_id, unit_id, command_id, status, note,
                            assigned_by, deadline, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                task.getId(), task.getIncidentId(), task.getUnitId(), task.getCommandId(),
                task.getStatus().name(), task.getNote(), task.getAssignedBy(),
                Timestamp.from(task.getDeadline()), task.getVersion(),
                Timestamp.from(task.getCreatedAt()), Timestamp.from(task.getUpdatedAt()));
    }

    @Override
    public int casUpdate(DispatchTask task, long expectedRowVersion) {
        return jdbc.update("""
                        UPDATE dispatch_task SET status = ?, note = ?, version = ?, updated_at = ?
                        WHERE id = ? AND version = ?
                        """,
                task.getStatus().name(), task.getNote(),
                expectedRowVersion + 1, Timestamp.from(task.getUpdatedAt()),
                task.getId(), expectedRowVersion);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
