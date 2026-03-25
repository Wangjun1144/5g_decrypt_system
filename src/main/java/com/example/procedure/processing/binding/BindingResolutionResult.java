package com.example.procedure.processing.binding;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.result.ResultMetadata;
import com.example.procedure.processing.result.ResultStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 身份绑定阶段的输出结果。
 *
 * 设计目的：
 * 1. 对齐文档中 Stage 3 的输出语义：
 *    - 绑定后的消息
 *    - 待缓冲消息
 *    - 释放后的 pending 消息
 * 2. 为后续把 callback 风格逐步改造成结果对象风格做准备
 * 3. 当前阶段仍可由旧 UeIdBinder 继续包装成 downstream callback 方式
 */
public class BindingResolutionResult {

    /**
     * 当前消息是否被缓冲；若为 true，则当前轮不应继续下游处理。
     */
    private final boolean buffered;

    /**
     * 当前已完成绑定、应继续进入下游的消息。
     */
    private final List<SignalingMessage> readyMessages;

    /**
     * 因本轮绑定成功而被释放的历史 pending 消息。
     */
    private final List<SignalingMessage> releasedMessages;

    /**
     * 构造 binding 结果对象。
     *
     * @param buffered 是否缓冲
     * @param readyMessages 当前 ready 消息列表
     * @param releasedMessages 当前 released 消息列表
     */
    private BindingResolutionResult(
            boolean buffered,
            List<SignalingMessage> readyMessages,
            List<SignalingMessage> releasedMessages
    ) {
        this.buffered = buffered;
        this.readyMessages = readyMessages == null ? List.of() : Collections.unmodifiableList(readyMessages);
        this.releasedMessages = releasedMessages == null ? List.of() : Collections.unmodifiableList(releasedMessages);
    }

    /**
     * 构造缓冲结果。
     *
     * @return 缓冲结果
     */
    public static BindingResolutionResult buffered() {
        return new BindingResolutionResult(true, List.of(), List.of());
    }

    /**
     * 构造 ready 结果。
     *
     * @param current 当前消息
     * @param released 被释放的历史 pending 消息
     * @return ready 结果
     */
    public static BindingResolutionResult ready(SignalingMessage current, List<SignalingMessage> released) {
        List<SignalingMessage> ready = new ArrayList<>();
        if (current != null) {
            ready.add(current);
        }
        return new BindingResolutionResult(false, ready, released);
    }

    /**
     * 判断当前是否为缓冲结果。
     *
     * @return true 表示缓冲
     */
    public boolean isBuffered() {
        return buffered;
    }

    /**
     * 获取 ready 消息列表。
     *
     * @return ready 消息列表
     */
    public List<SignalingMessage> getReadyMessages() {
        return readyMessages;
    }

    /**
     * 获取 released 消息列表。
     *
     * @return released 消息列表
     */
    public List<SignalingMessage> getReleasedMessages() {
        return releasedMessages;
    }

    /**
     * 为兼容旧 callback 风格而提供的总输出顺序。
     *
     * 保持与原 UeIdBinder 一致：
     * 1. 先 flush 历史 pending
     * 2. 最后再处理当前消息
     *
     * @return 下游处理顺序
     */
    public List<SignalingMessage> toDownstreamOrder() {
        List<SignalingMessage> result = new ArrayList<>(releasedMessages.size() + readyMessages.size());
        result.addAll(releasedMessages);
        result.addAll(readyMessages);
        return result;
    }

    /**
     * 转换成统一结果元数据。
     *
     * 这里的状态语义是：
     * - buffered=true -> BUFFERED
     * - 否则 -> SUCCESS
     *
     * @return 统一结果元数据
     */
    // REFACTOR STEP: RESULT_METADATA_CONTRACT
    public ResultMetadata toResultMetadata() {
        String primaryId = null;
        if (!readyMessages.isEmpty() && readyMessages.get(0) != null) {
            primaryId = readyMessages.get(0).getMsgId();
        }

        String message = buffered
                ? "binding buffered"
                : "binding ready=" + readyMessages.size() + ",released=" + releasedMessages.size();

        return new ResultMetadata(
                "BindingResolutionResult",
                buffered ? ResultStatus.BUFFERED : ResultStatus.SUCCESS,
                primaryId,
                message
        );
    }
}
