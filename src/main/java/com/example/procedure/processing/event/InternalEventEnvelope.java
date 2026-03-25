package com.example.procedure.processing.event;

/**
 * 内部事件 envelope。
 *
 * 当前用途：
 * 1. 把“公共元数据”和“具体事件载荷”统一包起来
 * 2. 让内部日志发布、审计发布、后续消息总线发布都使用一致的数据外形
 * 3. 避免每种事件都重复拼装同样的公共字段
 *
 * @param <T> 具体事件载荷类型
 */
public class InternalEventEnvelope<T> {

    /**
     * 事件公共元数据。
     */
    private final InternalEventMetadata metadata;

    /**
     * 事件载荷。
     */
    private final T payload;

    /**
     * 构造内部事件 envelope。
     *
     * @param metadata 公共元数据
     * @param payload 事件载荷
     */
    public InternalEventEnvelope(InternalEventMetadata metadata, T payload) {
        this.metadata = metadata;
        this.payload = payload;
    }

    /**
     * 获取公共元数据。
     *
     * @return 公共元数据
     */
    public InternalEventMetadata getMetadata() {
        return metadata;
    }

    /**
     * 获取事件载荷。
     *
     * @return 事件载荷
     */
    public T getPayload() {
        return payload;
    }
}
