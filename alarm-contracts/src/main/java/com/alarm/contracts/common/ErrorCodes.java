package com.alarm.contracts.common;

/** 统一错误码，与 HTTP 状态映射见详细设计文档。 */
public final class ErrorCodes {
    /** 参数格式/枚举非法。 -> 400 */
    public static final String INVALID_ARGUMENT = "ALM-0901";
    /** 缺少 Idempotency-Key 或请求头不完整。 -> 400 */
    public static final String MISSING_IDEMPOTENCY_KEY = "ALM-2101";
    /** 未认证（缺 X-Operator-Id）。 -> 401 */
    public static final String UNAUTHENTICATED = "ALM-1101";
    /** 无权限。 -> 403 */
    public static final String FORBIDDEN = "ALM-1102";
    /** 乐观锁版本冲突。 -> 409 */
    public static final String VERSION_CONFLICT = "ALM-1002";
    /** 状态机非法迁移/业务态冲突（含警力可用性竞争）。 -> 409 */
    public static final String STATE_CONFLICT = "ALM-1003";
    /** 资源不存在。 -> 404 */
    public static final String NOT_FOUND = "ALM-1004";
    /** 业务校验失败（缺结论、范围越界、无可用候选等）。 -> 422 */
    public static final String BUSINESS_VALIDATION = "ALM-1005";
    /** 幂等键冲突（同 key 不同请求体或进行中）。 -> 409 */
    public static final String IDEMPOTENCY_CONFLICT = "ALM-2102";
    /** 依赖不可用。 -> 503 */
    public static final String DEPENDENCY_UNAVAILABLE = "ALM-1301";
    /** 未分类服务端错误。 -> 500 */
    public static final String INTERNAL_ERROR = "ALM-9999";

    private ErrorCodes() {
    }
}
