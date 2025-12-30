package com.example.procedure.parser;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 通用的包解析上下文：
 *  - logicRoot: 当前 packet 的 layers（-T json）
 *  - rawRoot  : 当前 packet 的 layers（-T jsonraw），可以为 null
 *  - path     : 当前 DFS 在 logic 树里的路径（key 栈）
 *
 * 目前的 result 里只有 RRC + NAS，
 * 以后你完全可以在 RrcNasParseResult 里再加 NGAP / HTTP 等字段。
 */
public class PacketParseContext {

    /** 当前帧的全局 Info 计数器，每创建一个 Info 就 +1 */
    private int infoSequenceCounter = 0;

    /** 获取下一个 sequence 编号 */
    public int nextSequence() {
        return infoSequenceCounter++;
    }

    final JsonNode logicRoot;
    final JsonNode rawRoot;
    final RrcNasParseResult result;

    // 当前在 logic 树中的路径：["mac-nr", "nr-rrc", "UL_DCCH_Message_element", ...]
    final Deque<String> path = new ArrayDeque<>();

    // ⭐ 新增：MAC 阶段：>0 表示当前在 MAC 子树里
    int macDepth = 0;

    // ⭐ 新增：PDCP 阶段
    int pdcpDepth = 0;

    // ⭐ 新增：NGAP 阶段
    int ngapDepth = 0;

    // 阶段标志：>0 表示当前在 RRC 子树里
    int rrcDepth = 0;


    // ⭐ 新增：本帧的 MAC 信息（有可能根本没有 MAC）
    MacInfo macInfo;

    // ⭐ 新增
    PdcpInfo pdcpInfo;

    // 本帧的 RRC 信息（有可能根本没有 RRC）
    RrcInfo rrcInfo;

    // NAS 阶段：可能出现嵌套，所以用栈
    final Deque<NasInfo> nasStack = new ArrayDeque<>();

    // ⭐ NGAP：一帧里可能有多条 NGAP message，用栈来表示“当前正在处理哪一条”
    final Deque<NgapInfo> ngapStack = new ArrayDeque<>();

    // ⭐ 新增：本帧的 Nausf UE Auth Response
    NUARInfo nuarInfo;


    PacketParseContext(JsonNode logicRoot, JsonNode rawRoot, RrcNasParseResult result) {
        this.logicRoot = logicRoot;
        this.rawRoot = rawRoot;
        this.result = result;
    }

    // ⭐ 新增
    boolean inMac() { return macDepth > 0; }
    boolean inPdcp() { return pdcpDepth > 0; }
    boolean inRrc() { return rrcDepth > 0; }
    boolean inNas() { return !nasStack.isEmpty(); }
    // ⭐ 新增
    boolean inNgap() { return ngapDepth > 0; }

    NasInfo currentNas() { return nasStack.peek(); }

    // ⭐ 当前正在处理的 NGAP 消息
    NgapInfo currentNgap() { return ngapStack.peek(); }

    // ⭐ 新增：第一次用到时创建 MacInfo，并挂到 result 上
    MacInfo ensureMacInfo() {
        if (macInfo == null) {
            macInfo = new MacInfo();
            macInfo.setSequence(nextSequence());
            result.setMacInfo(macInfo);
        }
        return macInfo;
    }

    // ⭐ 新增：第一次用到时创建 PdcpInfo，并挂到 result 上
    PdcpInfo ensurePdcpInfo() {
        if (pdcpInfo == null) {
            pdcpInfo = new PdcpInfo();
            pdcpInfo.setSequence(nextSequence());
            result.setPdcpInfo(pdcpInfo);
        }
        return pdcpInfo;
    }

    // ⭐ 每遇到一条新的 NGAP message element 时调用
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
