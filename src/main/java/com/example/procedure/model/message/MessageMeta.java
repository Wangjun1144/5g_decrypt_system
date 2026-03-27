package com.example.procedure.model.message;

/**
 * 信令消息元信息
 *
 * 只保存“消息天然自带”的基础信息：
 * - 消息 ID
 * - 抓包帧号
 * - 时间戳
 * - 接口
 * - 方向
 *
 * 这样可以把 SignalingMessage 里最基础的一层信息收口。
 */
public class MessageMeta {

    /** 系统内部生成的消息 ID，例如 MSG-1 */
    private String msgId;

    /** 抓包中的 frame number */
    private Long frameNo;

    /** 消息时间戳（毫秒） */
    private Long timestamp;

    /** 接口，例如 UU / N2 / HTTP2 */
    private String iface;

    /** 方向，例如 UL / DL */
    private String direction;

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public Long getFrameNo() {
        return frameNo;
    }

    public void setFrameNo(long frameNo) {
        this.frameNo = frameNo;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getIface() {
        return iface;
    }

    public void setIface(String iface) {
        this.iface = iface;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
