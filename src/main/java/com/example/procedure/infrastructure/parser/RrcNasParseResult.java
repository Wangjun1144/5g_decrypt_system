package com.example.procedure.infrastructure.parser;

import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NUARInfo;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.model.message.info.RrcInfo;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 涓€鏉?packet 瑙ｆ瀽瀹屽悗杩斿洖鐨勭粨鏋滐細
 *  - rrcInfo锛氬鏋滄湁 RRC锛屽氨鍦ㄨ繖閲?
 *  - nasList锛氳繖鏉″寘閲屽嚭鐜扮殑鎵€鏈?nas-5gs锛堝寘鎷祵濂楀湪 NAS message container 閲岀殑锛?
 */
@Data
public class RrcNasParseResult {

    private MacInfo macInfo;

    // 猸?鏂板锛歅DCP 淇℃伅
    private PdcpInfo pdcpInfo;

    // 猸?NGAP锛氫竴甯ч噷鍙兘鏈夊鏉?NGAP PDU
    private List<NgapInfo> ngapList = new ArrayList<>();


    private RrcInfo rrcInfo;


    private List<NasInfo> nasList = new ArrayList<>();

    // 猸?鏂板锛歂ausf UE Authentication Response 淇℃伅锛坔ttp2:json锛?
    private NUARInfo nuarInfo;


    /** 杩欎竴甯ч噷鏄惁鍑虹幇鍔犲瘑 NAS锛堟湁浠绘剰涓€涓?NasInfo.encrypted=true 鍗冲彲缃?true锛?*/
    private boolean nasEncrypted;

    /** 绠€鍗曟爣涓€涓嬪姞瀵嗗眰锛岀洰鍓嶅氨鐢?"NAS"锛屼互鍚庡彲浠ユ墿灞?"PDCP" 绛?*/
    private String encryptedLayer;
}

