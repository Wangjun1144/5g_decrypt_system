package com.example.procedure.initial_acess;

import com.example.procedure.model.MessagePayload;
import lombok.Data;

@Data
public class NasSecurityModeCommandPayload implements MessagePayload {

    /** NAS 加密算法，如 NEA0/1/2/3 或对应枚举名/编号 */
    private String nasEncAlg;

    /** NAS 完整性保护算法，如 NIA0/1/2/3 */
    private String nasIntAlg;

    @Override
    public String getMsgType() {
        return "NAS SecurityModeCommand";
    }

    /** 阶段3起始：两种算法都解析出来才算OK */
    public boolean isStartMsg() {
        return nasEncAlg != null && !nasEncAlg.isEmpty()
                && nasIntAlg != null && !nasIntAlg.isEmpty();
    }
}
