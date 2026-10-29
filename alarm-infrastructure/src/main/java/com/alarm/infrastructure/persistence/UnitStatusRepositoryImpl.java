package com.alarm.infrastructure.persistence;

import com.alarm.application.port.UnitStatusRepository;
import com.alarm.contracts.common.LocationDto;
import com.alarm.contracts.enums.LocationSource;
import com.alarm.domain.model.UnitStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class UnitStatusRepositoryImpl implements UnitStatusRepository {

    private static final RowMapper<UnitStatus> MAPPER = (rs, i) -> {
        UnitStatus unit = new UnitStatus();
        unit.setUnitId(rs.getString("unit_id"));
        unit.setName(rs.getString("name"));
        String caps = rs.getString("capabilities");
        unit.setCapabilities(caps == null || caps.isBlank()
                ? new ArrayList<>() : Arrays.asList(caps.split(",")));
        unit.setAvailability(rs.getInt("availability"));
        double x = rs.getDouble("location_x");
        if (!rs.wasNull()) {
            unit.setLocation(new LocationDto(x, rs.getDouble("location_y"),
                    LocationSource.valueOf(rs.getString("location_source")),
                    toInstant(rs.getTimestamp("location_observed_at"))));
        }
        unit.setLastDispatchId(rs.getString("last_dispatch_id"));
        unit.setVersion(rs.getLong("version"));
        return unit;
    };

    private final JdbcTemplate jdbc;

    public UnitStatusRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UnitStatus> findById(String unitId) {
        return jdbc.query("SELECT * FROM unit_status WHERE unit_id = ?", MAPPER, unitId).stream().findFirst();
    }

    @Override
    public List<UnitStatus> listAll() {
        return jdbc.query("SELECT * FROM unit_status ORDER BY unit_id", MAPPER);
    }

    @Override
    public void insert(UnitStatus unit) {
        jdbc.update("""
                        INSERT INTO unit_status (unit_id, name, capabilities, availability,
                            location_x, location_y, location_source, location_observed_at,
                            last_dispatch_id, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                unit.getUnitId(), unit.getName(), joinCaps(unit.getCapabilities()),
                unit.getAvailability(), x(unit), y(unit), src(unit), ts(unit),
                unit.getLastDispatchId(), unit.getVersion());
    }

    @Override
    public int casUpdate(UnitStatus unit, long expectedRowVersion) {
        return jdbc.update("""
                        UPDATE unit_status SET name = ?, capabilities = ?, availability = ?,
                            location_x = ?, location_y = ?, location_source = ?, location_observed_at = ?,
                            version = ?
                        WHERE unit_id = ? AND version = ?
                        """,
                unit.getName(), joinCaps(unit.getCapabilities()), unit.getAvailability(),
                x(unit), y(unit), src(unit), ts(unit),
                expectedRowVersion + 1,
                unit.getUnitId(), expectedRowVersion);
    }

    @Override
    public boolean tryReserve(String unitId, String dispatchId, int amount) {
        return jdbc.update("""
                        UPDATE unit_status SET availability = availability - ?, last_dispatch_id = ?,
                            version = version + 1
                        WHERE unit_id = ? AND availability >= ?
                        """,
                amount, dispatchId, unitId, amount) > 0;
    }

    @Override
    public void release(String unitId, int amount) {
        jdbc.update("""
                        UPDATE unit_status SET availability = LEAST(100, availability + ?), version = version + 1
                        WHERE unit_id = ?
                        """,
                amount, unitId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String joinCaps(List<String> capabilities) {
        return capabilities == null ? "" : String.join(",", capabilities);
    }

    private static Double x(UnitStatus unit) {
        return unit.getLocation() == null ? null : unit.getLocation().x();
    }

    private static Double y(UnitStatus unit) {
        return unit.getLocation() == null ? null : unit.getLocation().y();
    }

    private static String src(UnitStatus unit) {
        return unit.getLocation() == null ? null : unit.getLocation().source().name();
    }

    private static Timestamp ts(UnitStatus unit) {
        return unit.getLocation() == null || unit.getLocation().observedAt() == null
                ? null : Timestamp.from(unit.getLocation().observedAt());
    }
}
