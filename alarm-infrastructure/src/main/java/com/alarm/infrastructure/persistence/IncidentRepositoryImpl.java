package com.alarm.infrastructure.persistence;

import com.alarm.application.port.IncidentRepository;
import com.alarm.contracts.common.LocationDto;
import com.alarm.contracts.enums.Channel;
import com.alarm.contracts.enums.IncidentStatus;
import com.alarm.contracts.enums.LocationSource;
import com.alarm.contracts.enums.Priority;
import com.alarm.domain.model.Incident;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** H2/生产关系库通用 JDBC 实现；条件更新带 WHERE version，0 行即乐观锁冲突。 */
@Repository
public class IncidentRepositoryImpl implements IncidentRepository {

    private static final RowMapper<Incident> MAPPER = (rs, i) -> {
        Incident incident = new Incident();
        incident.setId(rs.getString("id"));
        incident.setChannel(Channel.valueOf(rs.getString("channel")));
        incident.setSourceId(rs.getString("source_id"));
        incident.setStatus(IncidentStatus.valueOf(rs.getString("status")));
        incident.setPriority(Priority.valueOf(rs.getString("priority")));
        incident.setType(rs.getString("type"));
        incident.setDescription(rs.getString("description"));
        incident.setVerifiedAddress(rs.getString("verified_address"));
        double x = rs.getDouble("location_x");
        if (!rs.wasNull()) {
            LocationSource source = LocationSource.valueOf(rs.getString("location_source"));
            Instant observedAt = toInstant(rs.getTimestamp("location_observed_at"));
            incident.setLocation(new LocationDto(x, rs.getDouble("location_y"), source, observedAt));
        }
        incident.setConclusion(rs.getString("conclusion"));
        incident.setCreatedBy(rs.getString("created_by"));
        incident.setVersion(rs.getLong("version"));
        incident.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        incident.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        incident.setClosedAt(toInstant(rs.getTimestamp("closed_at")));
        return incident;
    };

    private final JdbcTemplate jdbc;

    public IncidentRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Incident> findById(String id) {
        List<Incident> list = jdbc.query(
                "SELECT * FROM incident WHERE id = ?", MAPPER, id);
        return list.stream().findFirst();
    }

    @Override
    public Optional<Incident> findByChannelAndSourceId(Channel channel, String sourceId) {
        List<Incident> list = jdbc.query(
                "SELECT * FROM incident WHERE channel = ? AND source_id = ?", MAPPER,
                channel.name(), sourceId);
        return list.stream().findFirst();
    }

    @Override
    public void insert(Incident incident) {
        jdbc.update("""
                        INSERT INTO incident (id, channel, source_id, status, priority, type, description,
                            verified_address, location_x, location_y, location_source, location_observed_at,
                            conclusion, created_by, version, created_at, updated_at, closed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                incident.getId(), incident.getChannel().name(), incident.getSourceId(),
                incident.getStatus().name(), incident.getPriority().name(), incident.getType(),
                incident.getDescription(), incident.getVerifiedAddress(),
                x(incident), y(incident), src(incident), ts(incident, "observedAt"),
                incident.getConclusion(), incident.getCreatedBy(), incident.getVersion(),
                ts(incident.getCreatedAt()), ts(incident.getUpdatedAt()), ts(incident.getClosedAt()));
    }

    @Override
    public int casUpdate(Incident incident, long expectedRowVersion) {
        return jdbc.update("""
                        UPDATE incident SET status = ?, priority = ?, type = ?, description = ?,
                            verified_address = ?, location_x = ?, location_y = ?, location_source = ?,
                            location_observed_at = ?, conclusion = ?, closed_at = ?,
                            version = ?, updated_at = ?
                        WHERE id = ? AND version = ?
                        """,
                incident.getStatus().name(), incident.getPriority().name(), incident.getType(),
                incident.getDescription(), incident.getVerifiedAddress(),
                x(incident), y(incident), src(incident), ts(incident, "observedAt"),
                incident.getConclusion(), ts(incident.getClosedAt()),
                expectedRowVersion + 1, ts(incident.getUpdatedAt()),
                incident.getId(), expectedRowVersion);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Double x(Incident incident) {
        return incident.getLocation() == null ? null : incident.getLocation().x();
    }

    private static Double y(Incident incident) {
        return incident.getLocation() == null ? null : incident.getLocation().y();
    }

    private static String src(Incident incident) {
        return incident.getLocation() == null ? null : incident.getLocation().source().name();
    }

    /** location.observed_at 提取（避免在 SQL 参数列表里重复 4 个 getter 调用）。 */
    private static Timestamp ts(Incident incident, String ignored) {
        return incident.getLocation() == null ? null : ts(incident.getLocation().observedAt());
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
