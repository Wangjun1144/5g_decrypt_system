package com.example.procedure.initial_acess;

import com.example.procedure.model.MessagePayload;
import lombok.Data;

@Data
public class NausfUeAuthRespPayload implements MessagePayload {

    /** SUPI，用于唯一标识 UE（IMSI/5G-GUTI 解析之后的标识） */
    private String supi;

    /** 该 UE 对应的 KSEAF 密钥（hex 或 base64，按你解析决定） */
    private String kseaf;

    @Override
    public String getMsgType() {
        return "Nausf_UEAuthentication_Authenticate Response";
    }

    /** 阶段2起始：必须拿到 SUPI 和 KSEAF 才算成功 */
    public boolean isStartMsg() {
        return supi != null && !supi.isEmpty()
                && kseaf != null && !kseaf.isEmpty();
    }
}

