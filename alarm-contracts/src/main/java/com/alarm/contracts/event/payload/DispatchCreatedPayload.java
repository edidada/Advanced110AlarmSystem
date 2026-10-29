package com.alarm.contracts.event.payload;

import java.time.Instant;

/** DispatchCreated 事件负载，含供终端幂等执行的 commandId。 */
public record DispatchCreatedPayload(String dispatchId, String unitId, String commandId, Instant deadline) {
}
