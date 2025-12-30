package com.example.procedure.model;

import lombok.Data;

@Data
public class Procedure {
    private String procedureId;
    private String ueId;
    private String procedureType;
    private String procedureTypeCode;

    private String lastMessageType;
    private String activateTime;
    private String lastUpdateTime;
    private int messageNum;
    private String endTime;

    private int lastPhaseIndex = -1;
    private int lastOrderIndex = -1;

    // ===== 新增：乱序结束控制 =====
    private boolean endSeen = false;
    private long endSeenAtMs = 0L;

    /** 关键消息到齐位图 */
    private int keyMask = 0;
}
