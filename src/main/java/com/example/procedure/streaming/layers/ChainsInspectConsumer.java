package com.example.procedure.streaming.layers;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.streaming.parser.RrcNasParseResult;
import com.example.procedure.parser.MacInfo;
import com.example.procedure.parser.NgapInfo;
import com.example.procedure.parser.NasInfo;
import com.example.procedure.parser.PdcpInfo;
import com.example.procedure.parser.RrcInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ChainsInspectConsumer implements Consumer<List<RrcNasParseResult>> {

    private static final AtomicLong SEQ = new AtomicLong(0);

    private final Consumer<SignalingMessage> onMessage;

    public ChainsInspectConsumer(Consumer<SignalingMessage> onMessage) {
        this.onMessage = onMessage;
    }

    @Override
    public void accept(List<RrcNasParseResult> chains) {
        if (chains == null || chains.isEmpty()) return;

        for (RrcNasParseResult chain : chains) {
            SignalingMessage msg = buildMessage(chain);
            if (msg != null) {
                long id = SEQ.incrementAndGet();
                msg.setMsgId("MSG-" + id);
                onMessage.accept(msg);
                // 你想 dump 或者传上层就行
            }
        }
    }

    private SignalingMessage buildMessage(RrcNasParseResult chain) {
        if (chain == null) return null;

        SignalingMessage msg = new SignalingMessage();

        // 1) frame meta
        msg.setFrameNo(chain.getFrameNo());
        msg.setTimestamp(chain.getTimestampMs());

        // 2) 直接搬运：iface / direction / layer / type / ueId
        msg.setIface(chain.getIface());              // ✅ 解析时已填：Uu / N2 / N12
        msg.setDirection(chain.getDirection());      // ✅ 解析时已填：UL / DL

        // msgType：两种方式任选其一
        // A) 解析时已规范化 msgType（推荐）
        msg.setMsgType(RrcNasParseResult.normalizedMsgType(chain.getMsgCode()));

        // B) 解析时只填了 msgCode，这里映射成规范化 msgType
        // msg.setMsgType(MsgTypeNameTable.nameOf(chain.getMsgCode()));

        msg.setUeId(chain.getUeId());                // ✅ NUAR 有就有，没有就 null

        // 3) 加密信息（你说解析期也能填好）
        msg.setEncrypted(chain.getEncrypted());
        msg.setEncryptedType(chain.getEncryptedType());

        // 4) 挂载各层对象（链里是 list，你说一条链一个 message，那取第一个即可）
        msg.setMacInfo(first(chain.getMacList()));
        msg.setPdcpInfo(first(chain.getPdcpList()));
        msg.setRrcInfo(first(chain.getRrcList()));
        msg.setNgapInfoList(chain.getNgapList() == null ? List.of() : chain.getNgapList());
        msg.setNuarInfo(chain.getNuarInfo());
        msg.setNasList(chain.getNasList() == null ? List.of() : chain.getNasList());

        return msg;
    }

    private static <T> T first(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }


    private void dumpChain(RrcNasParseResult chain) {
        // === 1) packet/frame 元信息（你在 parseLayersObject 里 set 进去的）===
        long frameNo = chain.getFrameNo();
        long tsMs = chain.getTimestampMs();
        String protos = chain.getFrameProtocols();
        List<String> protoList = chain.getProtoList();

        System.out.println("=== CHAIN ===");
        System.out.println("frameNo=" + frameNo + ", tsMs=" + tsMs);
        System.out.println("protocols=" + protos);
        System.out.println("protoList=" + protoList);

        // === 2) 加密标记（你在 NAS/PDCP/RRC 的解析里会设置）===
        System.out.println("nasEncrypted=" + chain.getEncryptedType()
                + ", encryptedLayer=" + chain.getEncrypted());

        // === 3) MAC/PDCP/RRC/NAS/NGAP 列表（通常每条链里可能有0/1/多条）===
        dumpMac(chain.getMacList());
        dumpPdcp(chain.getPdcpList());
        dumpRrc(chain.getRrcList());
        dumpNas(chain.getNasList());
        dumpNgap(chain.getNgapList());

        // === 4) 如果你需要索引/路径信息（ChainIndex）===
        // ChainIndex idx = chain.getIndex();
        // 取决于你 ChainIndex 暴露了什么 getter，比如 toJson()/toString()/getNodes() 等
    }

    private void dumpMac(List<MacInfo> list) {
        if (list == null || list.isEmpty()) return;
        for (MacInfo mac : list) {
            System.out.println("[MAC] rnti=" + mac.getRnti()
                    + ", rntiType=" + mac.getRntiType());
        }
    }

    private void dumpPdcp(List<PdcpInfo> list) {
        if (list == null || list.isEmpty()) return;
        for (PdcpInfo pdcp : list) {
            System.out.println("[PDCP] dir=" + pdcp.getDirection()
                    + ", seq=" + pdcp.getSeqnum()
                    + ", mac=" + pdcp.getMacHex()
                    + ", signalling=" + pdcp.getSignallingDataHex()
                    + ", encrypted=" + pdcp.isPdcpencrypted());
        }
    }

    private void dumpRrc(List<RrcInfo> list) {
        if (list == null || list.isEmpty()) return;
        for (RrcInfo rrc : list) {
            System.out.println("[RRC] msg=" + rrc.getMsgName()
                    + ", dir=" + rrc.getDirection()
                    + ", crnti=" + rrc.getCrnti()
                    + ", cipherAlgo=" + rrc.getCipheringAlgorithm()
                    + ", integAlgo=" + rrc.getIntegrityProtAlgorithm()
                    + ", hasDedicatedNas=" + rrc.isHasDedicatedNas());
        }
    }

    private void dumpNas(List<NasInfo> list) {
        if (list == null || list.isEmpty()) return;
        for (NasInfo nas : list) {
            System.out.println("[NAS] mmType=" + nas.getMmMessageType()
                    + ", epd=" + nas.getEpd()
                    + ", sht=" + nas.getSecurityHeaderType()
                    + ", seq=" + nas.getSeqNo()
                    + ", mac=" + nas.getMsgAuthCodeHex()
                    + ", enc=" + nas.isEncrypted()
                    + ", fullPduHex=" + shorten(nas.getFullNasPduHex(), 32)
                    + ", guami=" + nas.getGuamiMcc() + "-" + nas.getGuamiMnc()
                    + ", tmsi=" + nas.getTmsi());
        }
    }

    private void dumpNgap(List<NgapInfo> list) {
        if (list == null || list.isEmpty()) return;
        for (NgapInfo ngap : list) {
            System.out.println("[NGAP] pduType=" + ngap.getPduType()
                    + ", msg=" + ngap.getMsgName()
                    + ", dir=" + ngap.getDirection()
                    + ", ranUeId=" + ngap.getRanUeNgapId()
                    + ", secKey=" + shorten(ngap.getSecurityKeyHex(), 16));
        }
    }

    private static String shorten(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
