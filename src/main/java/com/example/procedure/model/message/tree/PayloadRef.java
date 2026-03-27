package com.example.procedure.model.message.tree;

public class PayloadRef {

    private PayloadType payloadType;
    private Integer payloadIndex;

    public PayloadRef() {
    }

    public PayloadRef(PayloadType payloadType, Integer payloadIndex) {
        this.payloadType = payloadType;
        this.payloadIndex = payloadIndex;
    }

    public PayloadType getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(PayloadType payloadType) {
        this.payloadType = payloadType;
    }

    public Integer getPayloadIndex() {
        return payloadIndex;
    }

    public void setPayloadIndex(Integer payloadIndex) {
        this.payloadIndex = payloadIndex;
    }

    public String toKey() {
        return payloadType + ":" + payloadIndex;
    }
}
