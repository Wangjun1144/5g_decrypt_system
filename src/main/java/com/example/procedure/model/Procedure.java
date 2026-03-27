package com.example.procedure.model;

import lombok.Data;

/**
 * Runtime procedure state tracked for one UE.
 *
 * It records the current matched procedure identity, progress position, and
 * termination-related flags used by the procedure stage and state store.
 */
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

    private boolean endSeen = false;
    private long endSeenAtMs = 0L;

    /**
     * Bit mask indicating which key messages have already been observed.
     */
    private int keyMask = 0;
}
