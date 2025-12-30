package com.example.procedure.initial_acess;

import com.example.procedure.model.MessagePayload;
import lombok.Data;

@Data
public class RrcReconfigurationPayload implements MessagePayload {

    /** 是否携带 DRB UP 的安全配置 */
    private boolean hasDrbSecurityConfig;

    /** DRB UP 加密是否激活 */
    private boolean drbUpEncActivated;

    /** DRB UP 完整性保护是否激活 */
    private boolean drbUpIntActivated;

    @Override
    public String getMsgType() {
        return "RRCReconfiguration";
    }

    /** 阶段6起始：至少得有 DRB 安全配置并解析出激活状态 */
    public boolean isStartMsg() {
        return hasDrbSecurityConfig
                && (drbUpEncActivated || drbUpIntActivated);
    }
}

