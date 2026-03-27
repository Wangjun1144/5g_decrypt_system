package com.example.procedure.infrastructure.parser;

import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NUARInfo;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.model.message.info.RrcInfo;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 閫氱敤鐨勫寘瑙ｆ瀽涓婁笅鏂囷細
 *  - logicRoot: 褰撳墠 packet 鐨?layers锛?T json锛?
 *  - rawRoot  : 褰撳墠 packet 鐨?layers锛?T jsonraw锛夛紝鍙互涓?null
 *  - path     : 褰撳墠 DFS 鍦?logic 鏍戦噷鐨勮矾寰勶紙key 鏍堬級
 *
 * 鐩墠鐨?result 閲屽彧鏈?RRC + NAS锛?
 * 浠ュ悗浣犲畬鍏ㄥ彲浠ュ湪 RrcNasParseResult 閲屽啀鍔?NGAP / HTTP 绛夊瓧娈点€?
 */
public class PacketParseContext {

    /** 褰撳墠甯х殑鍏ㄥ眬 Info 璁℃暟鍣紝姣忓垱寤轰竴涓?Info 灏?+1 */
    private int infoSequenceCounter = 0;

    /** 鑾峰彇涓嬩竴涓?sequence 缂栧彿 */
    public int nextSequence() {
        return infoSequenceCounter++;
    }

    final JsonNode logicRoot;
    final JsonNode rawRoot;
    final RrcNasParseResult result;

    // 褰撳墠鍦?logic 鏍戜腑鐨勮矾寰勶細["mac-nr", "nr-rrc", "UL_DCCH_Message_element", ...]
    final Deque<String> path = new ArrayDeque<>();

    // 猸?鏂板锛歁AC 闃舵锛?0 琛ㄧず褰撳墠鍦?MAC 瀛愭爲閲?
    int macDepth = 0;

    // 猸?鏂板锛歅DCP 闃舵
    int pdcpDepth = 0;

    // 猸?鏂板锛歂GAP 闃舵
    int ngapDepth = 0;

    // 闃舵鏍囧織锛?0 琛ㄧず褰撳墠鍦?RRC 瀛愭爲閲?
    int rrcDepth = 0;


    // 猸?鏂板锛氭湰甯х殑 MAC 淇℃伅锛堟湁鍙兘鏍规湰娌℃湁 MAC锛?
    MacInfo macInfo;

    // 猸?鏂板
    PdcpInfo pdcpInfo;

    // 鏈抚鐨?RRC 淇℃伅锛堟湁鍙兘鏍规湰娌℃湁 RRC锛?
    RrcInfo rrcInfo;

    // NAS 闃舵锛氬彲鑳藉嚭鐜板祵濂楋紝鎵€浠ョ敤鏍?
    final Deque<NasInfo> nasStack = new ArrayDeque<>();

    // 猸?NGAP锛氫竴甯ч噷鍙兘鏈夊鏉?NGAP message锛岀敤鏍堟潵琛ㄧず鈥滃綋鍓嶆鍦ㄥ鐞嗗摢涓€鏉♀€?
    final Deque<NgapInfo> ngapStack = new ArrayDeque<>();

    // 猸?鏂板锛氭湰甯х殑 Nausf UE Auth Response
    NUARInfo nuarInfo;


    PacketParseContext(JsonNode logicRoot, JsonNode rawRoot, RrcNasParseResult result) {
        this.logicRoot = logicRoot;
        this.rawRoot = rawRoot;
        this.result = result;
    }

    // 猸?鏂板
    boolean inMac() { return macDepth > 0; }
    boolean inPdcp() { return pdcpDepth > 0; }
    boolean inRrc() { return rrcDepth > 0; }
    boolean inNas() { return !nasStack.isEmpty(); }
    // 猸?鏂板
    boolean inNgap() { return ngapDepth > 0; }

    NasInfo currentNas() { return nasStack.peek(); }

    // 猸?褰撳墠姝ｅ湪澶勭悊鐨?NGAP 娑堟伅
    NgapInfo currentNgap() { return ngapStack.peek(); }

    // 猸?鏂板锛氱涓€娆＄敤鍒版椂鍒涘缓 MacInfo锛屽苟鎸傚埌 result 涓?
    MacInfo ensureMacInfo() {
        if (macInfo == null) {
            macInfo = new MacInfo();
            macInfo.setSequence(nextSequence());
            result.setMacInfo(macInfo);
        }
        return macInfo;
    }

    // 猸?鏂板锛氱涓€娆＄敤鍒版椂鍒涘缓 PdcpInfo锛屽苟鎸傚埌 result 涓?
    PdcpInfo ensurePdcpInfo() {
        if (pdcpInfo == null) {
            pdcpInfo = new PdcpInfo();
            pdcpInfo.setSequence(nextSequence());
            result.setPdcpInfo(pdcpInfo);
        }
        return pdcpInfo;
    }

    // 猸?姣忛亣鍒颁竴鏉℃柊鐨?NGAP message element 鏃惰皟鐢?
    NgapInfo pushNewNgap() {
        NgapInfo ngap = new NgapInfo();
        result.getNgapList().add(ngap);
        ngapStack.push(ngap);
        return ngap;
    }

    RrcInfo ensureRrcInfo() {
        if (rrcInfo == null) {
            rrcInfo = new RrcInfo();
            rrcInfo.setSequence(nextSequence());
            result.setRrcInfo(rrcInfo);
        }
        return rrcInfo;
    }

    NUARInfo ensureNuarInfo() {
        if (nuarInfo == null) {
            nuarInfo = new NUARInfo();
            nuarInfo.setSequence(nextSequence());
            result.setNuarInfo(nuarInfo);
        }
        return nuarInfo;
    }
}
