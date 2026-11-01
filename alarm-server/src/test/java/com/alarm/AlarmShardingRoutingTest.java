package com.alarm;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * sharding profile 路由回归（本地 H2 三库）：
 * 1) incident 按 incident_id HASH_MOD 2 落到 ds_0/ds_1，两分片都应有数据，总数守恒；
 * 2) 幂等/Outbox/审计等单表落在 sys 库（业务 SQL 无需感知）；
 * 3) 防重/幂等/分片点查语义与单库一致。
 */
@SpringBootTest(properties = {
        "spring.profiles.active=sharding",
        "alarm.storage.sharding.ds-0-url=jdbc:h2:mem:alarm_shard0;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "alarm.storage.sharding.ds-1-url=jdbc:h2:mem:alarm_shard1;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "alarm.storage.sharding.sys-url=jdbc:h2:mem:alarm_shard_sys;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "alarm.storage.sharding.username=sa",
        "alarm.storage.sharding.password=",
        "alarm.storage.sharding.seata-enabled=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
@TestPropertySource(properties = "spring.sql.init.schema-locations=classpath:schema.sql")
class AlarmShardingRoutingTest {

    static final String OP = "SHARD-OP";
    static final int CREATED = 18;

    @Autowired
    MockMvc mockMvc;

    @Test
    @Order(1)
    void createIncidents_distributedAcrossTwoShards() throws Exception {
        String firstId = null;
        for (int i = 0; i < CREATED; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/incidents")
                            .header("X-Operator-Id", OP)
                            .header("Idempotency-Key", "shard-create-" + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"channel":"PHONE","sourceId":"SHARD-%03d","priority":"HIGH",
                                     "type":"ROBBERY","description":"sharding routing case"}
                                    """.formatted(i)))
                    .andExpect(status().isCreated())
                    .andReturn();
            if (firstId == null) {
                firstId = json(result.getResponse().getContentAsString(), "$.incidentId");
            }
        }

        // 逻辑层可查（列表跨片合并）
        mockMvc.perform(get("/api/v1/incidents").header("X-Operator-Id", OP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(CREATED))
                .andExpect(jsonPath("$.items.length()").value(CREATED));

        // 物理层直查：两分片都应有数据且总数守恒
        int shard0 = countIn("jdbc:h2:mem:alarm_shard0;DB_CLOSE_DELAY=-1", "incident");
        int shard1 = countIn("jdbc:h2:mem:alarm_shard1;DB_CLOSE_DELAY=-1", "incident");
        assertThat(shard0 + shard1).isEqualTo(CREATED);
        assertThat(shard0).isPositive();
        assertThat(shard1).isPositive();
    }

    @Test
    @Order(2)
    void idempotencyAndOutboxStayInSysDatasource() throws Exception {
        // 18 张警单 -> 18 条 outbox（IncidentCreated）；防重返回既有警单时不应新增
        int outboxInSys = countIn("jdbc:h2:mem:alarm_shard_sys;DB_CLOSE_DELAY=-1", "outbox_event");
        assertThat(outboxInSys).isGreaterThanOrEqualTo(CREATED);

        // 防重：同 (channel, sourceId) 再次登记 -> 仍返回同一条，不新增 outbox
        mockMvc.perform(post("/api/v1/incidents")
                        .header("X-Operator-Id", OP)
                        .header("Idempotency-Key", "shard-create-dup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"PHONE","sourceId":"SHARD-000","priority":"HIGH",
                                 "type":"ROBBERY","description":"dedup probe"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
        assertThat(countIn("jdbc:h2:mem:alarm_shard_sys;DB_CLOSE_DELAY=-1", "outbox_event"))
                .isEqualTo(outboxInSys);
        assertThat(countOrMissing("jdbc:h2:mem:alarm_shard_sys;DB_CLOSE_DELAY=-1", "incident"))
                .as("sys 不存储 incident（应落于分片库）")
                .isLessThan(1);
        int outbox0 = countOrMissing("jdbc:h2:mem:alarm_shard0;DB_CLOSE_DELAY=-1", "outbox_event");
        int outbox1 = countOrMissing("jdbc:h2:mem:alarm_shard1;DB_CLOSE_DELAY=-1", "outbox_event");
        assertThat(Math.max(outbox0, outbox1))
                .as("outbox_event 不落分片库（表不存在或为空）")
                .isLessThan(1);
    }

    @Test
    @Order(3)
    void pointQueryRoutesByIncidentId() throws Exception {
        // 从列表取一条 -> 单查经分片路由应命中
        MvcResult list = mockMvc.perform(get("/api/v1/incidents?limit=1").header("X-Operator-Id", OP))
                .andExpect(status().isOk()).andReturn();
        String id = json(list.getResponse().getContentAsString(), "$.items[0].incidentId");
        mockMvc.perform(get("/api/v1/incidents/" + id).header("X-Operator-Id", OP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(id));
        // 审计查询（sys 单表）也应正常
        mockMvc.perform(get("/api/v1/audits/INCIDENT/" + id).header("X-Operator-Id", OP))
                .andExpect(status().isOk());
    }

    private static int countIn(String url, String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 表不存在视为 -1（“该分片不含此表”的等价表达）。 */
    private static int countOrMissing(String url, String table) {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String json(String body, String path) {
        return com.jayway.jsonpath.JsonPath.read(body, path);
    }
}
