package com.example.procedure.model;// package 按你的实际项目来写，比如 package com.xxx.service;

import com.example.procedure.model.ProcedureTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcedureMatchResult {

    /**
     * 0 = 成功找到/创建流程
     * 1 = 不属于任何流程（也没有创建 UNKNOWN）
     * 2 = 内部错误
     */
    private int status;

    /**
     * 错误或说明信息，可选
     */
    private String message;

    /**
     * 匹配到或新建的流程 ID
     */
    private String procedureId;

    /**
     * 流程类型枚举，例如 INITIAL_ACCESS / UNKNOWN
     */
    private ProcedureTypeEnum procedureType;

    /**
     * 是否是本次新创建的流程
     */
    private boolean newProcedure;

    // 一些便捷静态方法（可选，但用起来很舒服）

    public static ProcedureMatchResult successNew(String procedureId, ProcedureTypeEnum type) {
        return new ProcedureMatchResult(0, null, procedureId, type, true);
    }

    public static ProcedureMatchResult successExisting(String procedureId, ProcedureTypeEnum type) {
        return new ProcedureMatchResult(0, null, procedureId, type, false);
    }

    public static ProcedureMatchResult notMatched(String msg) {
        return new ProcedureMatchResult(1, msg, null, null, false);
    }

    public static ProcedureMatchResult error(String msg) {
        return new ProcedureMatchResult(2, msg, null, null, false);
    }
}
