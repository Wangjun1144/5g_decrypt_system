package com.example.procedure.initial_acess;

import com.example.procedure.model.MessagePayload;
import lombok.Data;

@Data
public class RrcSecurityModeCommandPayload implements MessagePayload {

    /** RRC 信令平面的加密算法 */
    private String rrcEncAlg;

    /** RRC 信令平面的完整性算法 */
    private String rrcIntAlg;

    /** UP （用户面）的加密算法，可选 */
    private String upEncAlg;

    /** UP （用户面）的完整性算法，可选 */
    private String upIntAlg;

    @Override
    public String getMsgType() {
        return "RRC SecurityModeCommand";
    }

    /** 阶段5起始：至少 RRC 的加密 + 完保要有；UP 算法可选 */
    public boolean isStartMsg() {
        return rrcEncAlg != null && !rrcEncAlg.isEmpty()
                && rrcIntAlg != null && !rrcIntAlg.isEmpty();
    }
}
