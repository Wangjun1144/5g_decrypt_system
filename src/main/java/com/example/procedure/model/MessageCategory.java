package com.example.procedure.model;

/**
 * 消息类别：
 *  - PROCEDURE_DRIVING：流程核心驱动信令
 *  - PROCEDURE_AUX：流程辅助信令
 *  - NON_PROCEDURE：非流程信令
 */
public enum MessageCategory {
    PROCEDURE_DRIVING,
    PROCEDURE_AUX,
    NON_PROCEDURE
}