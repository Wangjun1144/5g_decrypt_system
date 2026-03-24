package com.example.procedure.initial_access;

import com.example.procedure.model.MessagePayload;
import lombok.Data;

@Data
public class NgapInitialContextSetupReqPayload implements MessagePayload {

    /** 初始 KgNB（一般用 hex 字符串表示） */
    private String kgNb;

    @Override
    public String getMsgType() {
        return "Initial Context Setup Request";
    }

    /** 阶段 4 起始：必须能解析出 KgNB */
    public boolean isStartMsg() {
        return kgNb != null && !kgNb.isEmpty();
    }
}
