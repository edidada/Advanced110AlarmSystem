package com.alarm.storage;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;
/**
 * 警单分片算法：<code>ds = Math.floorMod(incident_id.hashCode(), sharding-count)</code>。
 * incident_id 由应用 UUID 生成器产生，hashCode 按规范稳定，路由确定且可独立断言；
 * 非分片键谓词（如 uk(channel, source_id) 查询）由 ShardingSphere 广播到两分片合并。
 */
public final class UuidHashShardAlgorithm implements StandardShardingAlgorithm<String> {

    private int shardingCount = 2;

    @Override
    public void init(Properties props) {
        String count = props.getProperty("sharding-count", "2");
        shardingCount = Math.max(1, Integer.parseInt(count.trim()));
    }

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<String> shardingValue) {
        if (shardingValue.getValue() == null) {
            throw new IllegalArgumentException("sharding value (incident_id) must be provided");
        }
        String suffix = "_" + Math.floorMod(shardingValue.getValue().hashCode(), shardingCount);
        return availableTargetNames.stream()
                .filter(name -> name.endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "sharding target not found for suffix " + suffix + " in " + availableTargetNames));
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<String> shardingValue) {
        throw new UnsupportedOperationException(
                "incident_id range sharding is not defined; incident_id is a random UUID");
    }

    @Override
    public String getType() {
        return "ALARM.UUID.HASH";
    }
}
