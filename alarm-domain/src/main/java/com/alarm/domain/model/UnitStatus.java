package com.alarm.domain.model;

import com.alarm.contracts.common.LocationDto;

import java.util.ArrayList;
import java.util.List;

/** 警力状态快照。availability 0~100，每次派遣预占 DISPATCH_RESERVE，任务终态时释放。 */
public class UnitStatus {
    private String unitId;
    private String name;
    private List<String> capabilities = new ArrayList<>();
    private int availability;
    private LocationDto location;
    private String lastDispatchId;
    private long version;

    public UnitStatus() {
    }

    public UnitStatus(String unitId, String name, List<String> capabilities, int availability,
                      LocationDto location, String lastDispatchId, long version) {
        this.unitId = unitId;
        this.name = name;
        this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
        this.availability = availability;
        this.location = location;
        this.lastDispatchId = lastDispatchId;
        this.version = version;
    }

    public String getUnitId() {
        return unitId;
    }

    public void setUnitId(String unitId) {
        this.unitId = unitId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
    }

    public int getAvailability() {
        return availability;
    }

    public void setAvailability(int availability) {
        this.availability = availability;
    }

    public LocationDto getLocation() {
        return location;
    }

    public void setLocation(LocationDto location) {
        this.location = location;
    }

    public String getLastDispatchId() {
        return lastDispatchId;
    }

    public void setLastDispatchId(String lastDispatchId) {
        this.lastDispatchId = lastDispatchId;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
