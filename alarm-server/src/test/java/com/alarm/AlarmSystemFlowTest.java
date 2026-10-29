package com.alarm;

import com.alarm.application.port.Clock;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 端到端业务闭环：登记/防重 -> 核实 -> 推荐 -> 并发派遣预占 -> 回执链 -> 结案 -> 重开；超时与迟到回执。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:alarm_flow;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "alarm.unit-location-stale-seconds:120"
})
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
class AlarmSystemFlowTest {

    static final String OPER = "OPER-01";
    static final String DISP = "DISP-01";
    static final String UNIT_NEAR = "U-NEAR";
    static final String UNIT_FAR = "U-FAR";

    /** 可推进测试时钟（由 Spring 回调注册）。 */
    static volatile StepClock TIME;

    static String incidentId;
    static String nearDispatchId;
    static String nearCommandId;
    static String farDispatchId;

    /** 测试时钟实现：StepClock 可显式快进。 */
    static class StepClock implements Clock {
        private volatile Instant now = Instant.parse("2026-10-29T02:00:00Z");

        @Override
        public Instant now() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    @TestConfiguration
    static class ClockCfg {
        @Bean
        @Primary
        public StepClock stepClock() {
            TIME = new StepClock();
            return TIME;
        }
    }

    @Autowired
    MockMvc mockMvc;

    // ===================== 1. 登记与防重复建单 =====================

    @Test
    @Order(1)
    void registerIncident_idempotentAgainstDuplicateSource() throws Exception {
        String body = """
                {
                  "channel": "PHONE",
                  "sourceId": "CALL-0001",
                  "priority": "HIGH",
                  "type": "ROBBERY",
                  "description": "street robbery",
                  "location": {"x": 116.3974, "y": 39.9092, "source": "CELL",
                               "observedAt": "%s"}
                }
                """.formatted(TIME.now().minusSeconds(10));

        MvcResult result = postJson("/api/v1/incidents", body, OPER, "idem-create-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        incidentId = jsonValue(result.getResponse().getContentAsString(), "$.incidentId");

        // 渠道与来源相同 -> 不重复建单，同一 incidentId
        postJson("/api/v1/incidents", body, OPER, "idem-create-2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentId").value(incidentId));

        // 同幂等键同请求 -> 返回缓存结果
        postJson("/api/v1/incidents", body, OPER, "idem-create-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentId").value(incidentId));

        // 同幂等键不同请求 -> 409 ALM-2102
        postJson("/api/v1/incidents", body.replace("ROBBERY", "BURGLARY"), OPER, "idem-create-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALM-2102"));
    }

    @Test
    @Order(2)
    void missingOperatorHeaderReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/" + incidentId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ALM-1101"));
    }

    @Test
    @Order(3)
    void verifyRequiresAddress_thenAccepts() throws Exception {
        patchIncident("""
                {"expectedVersion":0,"action":"VERIFY","reason":"verify","priority":"HIGH"}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALM-1005"));

        patchIncident("""
                {"expectedVersion":0,"action":"VERIFY","reason":"address verified",
                 "verifiedAddress":"Qianmen Street 1",
                 "location":{"x":116.3974,"y":39.9092,"source":"CELL","observedAt":"%s"}}
                """.formatted(TIME.now().minusSeconds(10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @Order(4)
    void staleVersionUpdateConflict409() throws Exception {
        patchIncident("""
                {"expectedVersion":0,"action":"UPDATE","priority":"CRITICAL"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALM-1002"));
    }

    // ===================== 2. 警力与推荐 =====================

    @Test
    @Order(5)
    void upsertUnits_nearHasCapacityForSingleReserve() throws Exception {
        postJson("/api/v1/units", """
                {"unitId":"%s","name":"patrol near","capabilities":["PATROL"],
                 "availability":20,
                 "location":{"x":116.3974,"y":39.9092,"source":"GPS","observedAt":"%s"}}
                """.formatted(UNIT_NEAR, TIME.now().minusSeconds(30)), OPER, "unit-near")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));

        postJson("/api/v1/units", """
                {"unitId":"%s","name":"patrol far","capabilities":["PATROL"],
                 "availability":20,"location":{"x":117.5,"y":41.0,"source":"GPS","observedAt":"%s"}}
                """.formatted(UNIT_FAR, TIME.now().minusSeconds(30)), OPER, "unit-far")
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void recommendationOnlyWithinRadius() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/recommendations-preview")
                        .header("X-Operator-Id", OPER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":"%s","radiusMeters":500}
                                """.formatted(incidentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].unitId").value(UNIT_NEAR));
    }

    // ===================== 3. 并发派遣与幂等 =====================

    @Test
    @Order(7)
    void concurrentDispatch_onlyOneReserveSucceeds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicReference<String> created = new AtomicReference<>();

        for (int t = 0; t < 2; t++) {
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await(5, TimeUnit.SECONDS);
                    MvcResult result = mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/dispatches")
                                    .header("X-Operator-Id", DISP)
                                    .header("Idempotency-Key", "idem-concurrent-" + Thread.currentThread().getId())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"expectedVersion":1,"unitIds":["%s"],"deadlineSeconds":30}
                                            """.formatted(UNIT_NEAR)))
                            .andReturn();
                    if (result.getResponse().getStatus() == 201) {
                        ok.incrementAndGet();
                        created.set(result.getResponse().getContentAsString());
                    } else {
                        conflict.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflict.incrementAndGet();
                }
            });
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);

        String body = created.get();
        assertThat(body).isNotNull();
        nearDispatchId = jsonValue(body, "$.dispatches[0].dispatchId");
        nearCommandId = jsonValue(body, "$.dispatches[0].commandId");
    }

    @Test
    @Order(8)
    void sameIdempotencyKeyReturnsCachedResult() throws Exception {
        for (int i = 0; i < 2; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/dispatches")
                            .header("X-Operator-Id", DISP)
                            .header("Idempotency-Key", "idem-far-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"expectedVersion":2,"unitIds":["%s"],"deadlineSeconds":60}
                                    """.formatted(UNIT_FAR)))
                    .andExpect(status().isCreated())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            assertThat(((List<?>) com.jayway.jsonpath.JsonPath.read(body, "$.dispatches")).size()).isEqualTo(1);
            farDispatchId = jsonValue(body, "$.dispatches[0].dispatchId");
        }
        // 取消派遣（终态协调），让后续近端任务链完成后警单可推进
        mockMvc.perform(post("/api/v1/dispatches/" + farDispatchId + "/cancel")
                        .header("X-Operator-Id", DISP)
                        .header("Idempotency-Key", "cancel-far-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"reason":"not needed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ===================== 4. 回执链与结案 =====================

    @Test
    @Order(9)
    void receiptChain_reachesFeedbackDone_andIncidentPendingReview() throws Exception {
        postJson("/api/v1/dispatches/" + nearDispatchId + "/receipts", """
                {"commandId":"%s","targetStatus":"ACCEPTED","expectedVersion":0,"note":"ok"}
                """.formatted(nearCommandId), "UNIT-" + UNIT_NEAR, "receipt-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.version").value(1));

        // 相同 commandId+targetStatus 重复回执 -> 返回已缓存状态，不重复推进
        postJson("/api/v1/dispatches/" + nearDispatchId + "/receipts", """
                {"commandId":"%s","targetStatus":"ACCEPTED","expectedVersion":0,"note":"ok"}
                """.formatted(nearCommandId), "UNIT-" + UNIT_NEAR, "receipt-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        postJson("/api/v1/dispatches/" + nearDispatchId + "/receipts", """
                {"commandId":"%s","targetStatus":"ENROUTE","expectedVersion":1}
                """.formatted(nearCommandId), "UNIT-" + UNIT_NEAR, "receipt-b")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        postJson("/api/v1/dispatches/" + nearDispatchId + "/receipts", """
                {"commandId":"%s","targetStatus":"ARRIVED","expectedVersion":2}
                """.formatted(nearCommandId), "UNIT-" + UNIT_NEAR, "receipt-c")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));

        postJson("/api/v1/dispatches/" + nearDispatchId + "/receipts", """
                {"commandId":"%s","targetStatus":"FEEDBACK_DONE","expectedVersion":3,"note":"done"}
                """.formatted(nearCommandId), "UNIT-" + UNIT_NEAR, "receipt-d")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));

        mockMvc.perform(get("/api/v1/incidents/" + incidentId).header("X-Operator-Id", OPER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.dispatches.length()").value(2));
    }

    @Test
    @Order(10)
    void closeRequiresConclusion_thenClosedAndIdempotent() throws Exception {
        postJson("/api/v1/incidents/" + incidentId + "/close", """
                {"expectedVersion":4,"conclusion":""}
                """, OPER, "close-empty")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALM-1005"));

        postJson("/api/v1/incidents/" + incidentId + "/close", """
                {"expectedVersion":4,"conclusion":"handled and confirmed"}
                """, OPER, "close-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.version").value(5));

        postJson("/api/v1/incidents/" + incidentId + "/close", """
                {"expectedVersion":4,"conclusion":"handled and confirmed"}
                """, OPER, "close-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(5));
    }

    @Test
    @Order(11)
    void reopenAndAuditTrail() throws Exception {
        patchIncident("""
                {"expectedVersion":5,"action":"REOPEN","reason":"new evidence"}
                """, "idem-reopen-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.version").value(6));

        mockMvc.perform(get("/api/v1/audits/INCIDENT/" + incidentId)
                        .header("X-Operator-Id", "AUDIT-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(2)))
                .andExpect(jsonPath("$[0].action").value("INCIDENT_CREATED"));
    }

    // ===================== 5. 超时与迟到回执 =====================

    @Test
    @Order(12)
    void timeoutScanMarksExpiredAndLateAcceptRejected() throws Exception {
        String inc2Json = postJson("/api/v1/incidents", """
                {"channel":"APP","sourceId":"APP-1002","priority":"MEDIUM","type":"NOISE",
                 "description":"noise complaint",
                 "location":{"x":116.4,"y":39.9,"source":"GPS","observedAt":"%s"}}
                """.formatted(TIME.now()), "OPER-02", "idem-create-2")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String inc2 = jsonValue(inc2Json, "$.incidentId");

        mockMvc.perform(patch("/api/v1/incidents/" + inc2)
                        .header("X-Operator-Id", "OPER-02")
                        .header("Idempotency-Key", "verify-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"action":"VERIFY","reason":"verified",
                                 "verifiedAddress":"North Street 9"}
                                """))
                .andExpect(status().isOk());

        String disp2Json = mockMvc.perform(post("/api/v1/incidents/" + inc2 + "/dispatches")
                        .header("X-Operator-Id", "DISP-02")
                        .header("Idempotency-Key", "dispatch-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1,"unitIds":["%s"],"deadlineSeconds":30}
                                """.formatted(UNIT_FAR)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String disp2 = jsonValue(disp2Json, "$.dispatches[0].dispatchId");
        String cmd2 = jsonValue(disp2Json, "$.dispatches[0].commandId");

        // 推进时钟让任务过期，执行超时扫描
        TIME.advance(Duration.ofSeconds(40));
        mockMvc.perform(post("/api/v1/ops/dispatch-timeout/run")
                        .header("X-Operator-Id", "OPS-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(greaterThanOrEqualTo(1)));

        // TIMEOUT 后迟到接单 -> 409 ALM-1003，不能激活过期任务
        mockMvc.perform(post("/api/v1/dispatches/" + disp2 + "/receipts")
                        .header("X-Operator-Id", "UNIT-" + UNIT_FAR)
                        .header("Idempotency-Key", "receipt-late-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commandId":"%s","targetStatus":"ACCEPTED","expectedVersion":0}
                                """.formatted(cmd2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALM-1003"));

        // 全部任务终态 -> 警单进入待复核
        mockMvc.perform(get("/api/v1/incidents/" + inc2).header("X-Operator-Id", "OPER-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    // ===================== helpers =====================

    private org.springframework.test.web.servlet.ResultActions patchIncident(String body) throws Exception {
        return patchIncident(body, "idem-patch-" + Math.abs(body.hashCode()));
    }

    private org.springframework.test.web.servlet.ResultActions patchIncident(
            String body, String idemKey) throws Exception {
        return mockMvc.perform(patch("/api/v1/incidents/" + incidentId)
                .header("X-Operator-Id", OPER)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(
            String url, String body, String operator, String idemKey) throws Exception {
        return mockMvc.perform(post(url)
                .header("X-Operator-Id", operator)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String jsonValue(String json, String path) {
        return com.jayway.jsonpath.JsonPath.read(json, path);
    }
}
