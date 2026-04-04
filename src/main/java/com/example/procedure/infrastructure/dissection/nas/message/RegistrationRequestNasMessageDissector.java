package com.example.procedure.infrastructure.dissection.nas.message;

import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsIeReader;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsMobileIdentityDecoder;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * First structured message template aligned with Wireshark's Registration
 * Request flow at a minimal IE level.
 */
@Component
public class RegistrationRequestNasMessageDissector implements Nas5gsMessageDissector {

    @Override
    public boolean supports(int messageType) {
        return messageType == 0x41;
    }

    @Override
    public String messageTypeName() {
        return "Registration request";
    }

    @Override
    public void dissect(
            PacketBuffer fullMessage,
            Nas5gsIeReader reader,
            int bodyOffset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(bodyOffset) < 1) {
            return;
        }
        int ngKsiAndRegType = reader.u8(bodyOffset);
        int regType = ngKsiAndRegType & 0x07;
        int tsc = (ngKsiAndRegType >>> 7) & 0x01;
        int nasKeySetId = (ngKsiAndRegType >>> 4) & 0x07;
        flatFields.put("nas-5gs.mm.5gs_reg_type", Integer.toString(regType));
        flatFields.put("nas-5gs.mm.tsc.h1", Integer.toString(tsc));
        flatFields.put("nas-5gs.mm.nas_key_set_id.h1", Integer.toString(nasKeySetId));
        messageNode.addChild(new DecodedFieldNode(
                "nas-5gs.mm.5gs_reg_type",
                Integer.toString(regType),
                bodyOffset,
                1
        ));
        messageNode.addChild(new DecodedFieldNode(
                "nas-5gs.mm.tsc.h1",
                Integer.toString(tsc),
                bodyOffset,
                1
        ));
        messageNode.addChild(new DecodedFieldNode(
                "nas-5gs.mm.nas_key_set_id.h1",
                Integer.toString(nasKeySetId),
                bodyOffset,
                1
        ));

        int mobileIdentityOffset = bodyOffset + 1;
        Nas5gsMobileIdentityDecoder.decodeRegistrationRequestIdentity(
                reader,
                mobileIdentityOffset,
                flatFields,
                messageNode
        );

        if (reader.remaining(mobileIdentityOffset) < 2) {
            return;
        }
        int optionalsOffset = mobileIdentityOffset + 2 + reader.u16(mobileIdentityOffset);
        scanOptionalInformationElements(reader, optionalsOffset, flatFields, messageNode);
    }

    private void scanOptionalInformationElements(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        int current = offset;
        while (reader.remaining(current) > 0) {
            int iei = reader.u8(current);
            int next = current;
            if ((iei & 0xf0) == 0xc0) {
                DecodedFieldNode node = new DecodedFieldNode(
                        "Non-current native NAS key set identifier",
                        "",
                        current,
                        1
                );
                messageNode.addChild(node);
                addField(node, flatFields, "nas-5gs.mm.nas_key_set_id", Integer.toString(iei & 0x07), current, 1);
                next = current + 1;
            } else if ((iei & 0xf0) == 0xb0) {
                DecodedFieldNode node = new DecodedFieldNode("MICO indication", "", current, 1);
                messageNode.addChild(node);
                addField(node, flatFields, "nas-5gs.mm.sprti_b1", Integer.toString((iei >>> 1) & 0x01), current, 1);
                addField(node, flatFields, "nas-5gs.mm.raai_b0", Integer.toString(iei & 0x01), current, 1);
                next = current + 1;
            } else if ((iei & 0xf0) == 0x80) {
                DecodedFieldNode node = new DecodedFieldNode("Payload container type", "", current, 1);
                messageNode.addChild(node);
                addField(node, flatFields, "nas-5gs.mm.pld_cont_type", Integer.toString(iei & 0x0f), current, 1);
                next = current + 1;
            } else if ((iei & 0xf0) == 0x90) {
                DecodedFieldNode node = new DecodedFieldNode("Network slicing indication", "", current, 1);
                messageNode.addChild(node);
                addField(node, flatFields, "nas-5gs.mm.network_slicing_indication", Integer.toString(iei & 0x0f), current, 1);
                next = current + 1;
            } else if ((iei & 0xf0) == 0xa0) {
                DecodedFieldNode node = new DecodedFieldNode("N5GC indication", "", current, 1);
                messageNode.addChild(node);
                addField(node, flatFields, "nas-5gs.mm.n5gc_indication", Integer.toString(iei & 0x0f), current, 1);
                next = current + 1;
            } else {
                switch (iei) {
                    case 0x10 -> next = decode5gmmCapabilityTlv(reader, current, flatFields, messageNode);
                    case 0x2e -> next = decodeUeSecurityCapabilityTlv(reader, current, flatFields, messageNode);
                    case 0x2f -> next = decodeRequestedNssaiTlv(reader, current, flatFields, messageNode);
                    case 0x52 -> next = decodeLastVisitedRegisteredTaiTv(reader, current, flatFields, messageNode);
                    case 0x17 -> next = decodeS1UeNetworkCapabilityTlv(reader, current, flatFields, messageNode);
                    case 0x40 -> next = decodePsiStatusTlv(reader, current, flatFields, messageNode, "Uplink data status", "nas-5gs.ul_data_sts_psi_");
                    case 0x50 -> next = decodePsiStatusTlv(reader, current, flatFields, messageNode, "PDU session status", "nas-5gs.pdu_ses_sts_psi_");
                    case 0x2b -> next = decodeUeStatusTlv(reader, current, flatFields, messageNode);
                    case 0x77 -> next = decodeAdditionalGutiTlvE(reader, current, flatFields, messageNode);
                    case 0x25 -> next = decodePsiStatusTlv(reader, current, flatFields, messageNode, "Allowed PDU session status", "nas-5gs.allow_pdu_ses_sts_psi_");
                    case 0x18 -> next = decodeUeUsageSettingTlv(reader, current, flatFields, messageNode);
                    case 0x51 -> next = decodeRequestedDrxParametersTlv(reader, current, flatFields, messageNode);
                    case 0x70 -> next = decodeEpsNasMessageContainerTlvE(reader, current, flatFields, messageNode);
                    case 0x74 -> next = decodeLadnIndicationTlvE(reader, current, flatFields, messageNode);
                    case 0x7b -> next = decodePayloadContainerTlvE(reader, current, flatFields, messageNode);
                    case 0x53 -> next = decode5gsUpdateTypeTlv(reader, current, flatFields, messageNode);
                    case 0x41 -> next = decodeGenericTlv(reader, current, "Mobile station classmark 2", messageNode);
                    case 0x42 -> next = decodeGenericTlv(reader, current, "Supported codecs", messageNode);
                    case 0x71 -> next = decodeGenericTlvE(reader, current, "NAS message container", messageNode);
                    case 0x60 -> next = decodeGenericTlv(reader, current, "EPS bearer context status", messageNode);
                    case 0x6e -> next = decodeGenericTlv(reader, current, "Requested extended DRX parameters", messageNode);
                    case 0x6a -> next = decodeGenericTlv(reader, current, "T3324 value", messageNode);
                    case 0x67 -> next = decodeGenericTlv(reader, current, "UE radio capability ID", messageNode);
                    case 0x35 -> next = decodeGenericTlv(reader, current, "Requested mapped NSSAI", messageNode);
                    case 0x48 -> next = decodeGenericTlv(reader, current, "Additional information requested", messageNode);
                    case 0x1a -> next = decodeGenericTlv(reader, current, "Requested WUS assistance information", messageNode);
                    case 0x30 -> next = decodeGenericTlv(reader, current, "Requested NB-N1 mode DRX parameters", messageNode);
                    case 0x29 -> next = decodeGenericTlv(reader, current, "UE request type", messageNode);
                    case 0x28 -> next = decodeGenericTlv(reader, current, "Paging restriction", messageNode);
                    case 0x72 -> next = decodeGenericTlvE(reader, current, "Service-level-AA container", messageNode);
                    case 0x32 -> next = decodeGenericTlv(reader, current, "NID", messageNode);
                    case 0x16 -> next = decodeGenericTlv(reader, current, "UE determined PLMN with disaster condition", messageNode);
                    case 0x2a -> next = decodeGenericTlv(reader, current, "Requested PEIPS assistance information", messageNode);
                    case 0x3b -> next = decodeGenericTlv(reader, current, "Requested T3512 value", messageNode);
                    case 0x3c -> next = decodeGenericTlv(reader, current, "Unavailability information", messageNode);
                    case 0x3f -> next = decodeGenericTlv(reader, current, "Non-3GPP path switching information", messageNode);
                    case 0x56 -> next = decodeGenericTlv(reader, current, "AUN3 indication", messageNode);
                    case 0x64 -> next = decodeGenericTlv(reader, current, "Requested LP-WUSPS assistance information", messageNode);
                    default -> {
                        return;
                    }
                }
            }
            if (next <= current) {
                return;
            }
            current = next;
        }
    }

    private int decode5gmmCapabilityTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("5GMM capability", "", offset, 2 + length);
        messageNode.addChild(node);
        addField(node, flatFields, "gsm_a.len", Integer.toString(length), offset + 1, 1);
        decode5gmmCapability(reader, offset + 2, length, flatFields, node);
        return offset + 2 + length;
    }

    private int decodeUeSecurityCapabilityTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int capabilityLength = reader.u8(offset + 1);
        if (reader.remaining(offset + 2) < capabilityLength) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("UE security capability", "", offset, 2 + capabilityLength);
        messageNode.addChild(node);
        addField(node, flatFields, "nas-5gs.mm.elem_id", "0x2e", offset, 1);
        addField(node, flatFields, "gsm_a.len", Integer.toString(capabilityLength), offset + 1, 1);
        if (capabilityLength >= 4) {
            int eaOctet = reader.u8(offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.5g_ea0", (eaOctet >>> 7) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.128_5g_ea1", (eaOctet >>> 6) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.128_5g_ea2", (eaOctet >>> 5) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.128_5g_ea3", (eaOctet >>> 4) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.5g_ea4", (eaOctet >>> 3) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.5g_ea5", (eaOctet >>> 2) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.5g_ea6", (eaOctet >>> 1) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.5g_ea7", eaOctet & 0x01, offset + 2);

            int iaOctet = reader.u8(offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.ia0", (iaOctet >>> 7) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.5g_128_ia1", (iaOctet >>> 6) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.5g_128_ia2", (iaOctet >>> 5) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.5g_128_ia3", (iaOctet >>> 4) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.5g_128_ia4", (iaOctet >>> 3) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.5g_ia5", (iaOctet >>> 2) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.5g_ia6", (iaOctet >>> 1) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.5g_ia7", iaOctet & 0x01, offset + 3);
        }
        return offset + 2 + capabilityLength;
    }

    private int decodeAdditionalGutiTlvE(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 2) {
            return offset;
        }
        int length = reader.u16(offset + 1);
        if (reader.remaining(offset + 3) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("Additional GUTI", "", offset, 3 + length);
        messageNode.addChild(node);
        Nas5gsMobileIdentityDecoder.decodeRegistrationRequestIdentity(
                reader,
                offset + 1,
                flatFields,
                node
        );
        return offset + 3 + length;
    }

    private int decodeLastVisitedRegisteredTaiTv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        int totalLength = 7;
        if (reader.remaining(offset) < totalLength) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("Last visited registered TAI", "", offset, totalLength);
        messageNode.addChild(node);
        int b1 = reader.u8(offset + 1);
        int b2 = reader.u8(offset + 2);
        int b3 = reader.u8(offset + 3);
        addField(node, flatFields, "e212.mcc", decodeMcc(b1, b2), offset + 1, 2);
        addField(node, flatFields, "e212.mnc", decodeMnc(b2, b3), offset + 2, 2);
        int tac = (reader.u8(offset + 4) << 16) | (reader.u8(offset + 5) << 8) | reader.u8(offset + 6);
        addField(node, flatFields, "nas-5gs.tac", Integer.toString(tac), offset + 4, 3);
        return offset + totalLength;
    }

    private int decodeRequestedNssaiTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("Requested NSSAI", "", offset, 2 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        int current = offset + 2;
        int index = 1;
        int end = current + length;
        while (current < end && reader.remaining(current) > 0) {
            int itemLength = reader.u8(current);
            if (itemLength < 1 || current + 1 + itemLength > end || reader.remaining(current + 1) < itemLength) {
                break;
            }
            DecodedFieldNode itemNode = new DecodedFieldNode("S-NSSAI " + index, "", current, 1 + itemLength);
            node.addChild(itemNode);
            itemNode.addChild(new DecodedFieldNode("Length", Integer.toString(itemLength), current, 1));
            decodeSnssai(reader, current + 1, itemLength, flatFields, itemNode);
            current += 1 + itemLength;
            index++;
        }
        return offset + 2 + length;
    }

    private int decodeS1UeNetworkCapabilityTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 2 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("S1 UE network capability", "", offset, 2 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));

        int current = offset + 2;
        decodeBitOctet(node, flatFields, current, reader.u8(current), new String[]{
                "nas-eps.emm.eea0",
                "nas-eps.emm.128eea1",
                "nas-eps.emm.128eea2",
                "nas-eps.emm.eea3",
                "nas-eps.emm.eea4",
                "nas-eps.emm.eea5",
                "nas-eps.emm.eea6",
                "nas-eps.emm.eea7"
        });
        current++;

        if ((current - (offset + 2)) >= length) {
            return offset + 2 + length;
        }
        decodeBitOctet(node, flatFields, current, reader.u8(current), new String[]{
                "nas-eps.emm.eia0",
                "nas-eps.emm.128eia1",
                "nas-eps.emm.128eia2",
                "nas-eps.emm.eia3",
                "nas-eps.emm.eia4",
                "nas-eps.emm.eia5",
                "nas-eps.emm.eia6",
                "nas-eps.emm.eps_upip"
        });
        current++;

        if ((current - (offset + 2)) >= length) {
            return offset + 2 + length;
        }
        decodeBitOctet(node, flatFields, current, reader.u8(current), new String[]{
                "nas-eps.emm.uea0",
                "nas-eps.emm.uea1",
                "nas-eps.emm.uea2",
                "nas-eps.emm.uea3",
                "nas-eps.emm.uea4",
                "nas-eps.emm.uea5",
                "nas-eps.emm.uea6",
                "nas-eps.emm.uea7"
        });
        current++;

        if ((current - (offset + 2)) >= length) {
            return offset + 2 + length;
        }
        decodeBitOctet(node, flatFields, current, reader.u8(current), new String[]{
                "nas-eps.emm.emm_ucs2_supp",
                "nas-eps.emm.uia1",
                "nas-eps.emm.uia2",
                "nas-eps.emm.uia3",
                "nas-eps.emm.uia4",
                "nas-eps.emm.uia5",
                "nas-eps.emm.uia6",
                "nas-eps.emm.uia7"
        });
        current++;

        if ((current - (offset + 2)) >= length) {
            return offset + 2 + length;
        }
        decodeBitOctet(node, flatFields, current, reader.u8(current), new String[]{
                "nas-eps.emm.prose_dd_cap",
                "nas-eps.emm.prose_cap",
                "nas-eps.emm.h245_ash_cap",
                "nas-eps.emm.acc_csfb_cap",
                "nas-eps.emm.lpp_cap",
                "nas-eps.emm.lcs_cap",
                "nas-eps.emm.1xsrvcc_cap",
                "nas-eps.emm.nf_cap"
        });
        current++;

        if ((current - (offset + 2)) >= length) {
            return offset + 2 + length;
        }
        decodeBitOctet(node, flatFields, current, reader.u8(current), new String[]{
                "nas-eps.emm.epco_cap",
                "nas-eps.emm.hc_cp_ciot_cap",
                "nas-eps.emm.er_wo_pdn_cap",
                "nas-eps.emm.s1u_data_cap",
                "nas-eps.emm.up_ciot_cap",
                "nas-eps.emm.cp_ciot_cap",
                "nas-eps.emm.prose_relay_cap",
                "nas-eps.emm.prose_dc_cap"
        });
        current++;

        if ((current - (offset + 2)) >= length) {
            return offset + 2 + length;
        }
        decodeBitOctet(node, flatFields, current, reader.u8(current), new String[]{
                "nas-eps.emm.15_bearers_cap",
                "nas-eps.emm.sgc_cap",
                "nas-eps.emm.n1mode_cap",
                "nas-eps.emm.dcnr_cap",
                "nas-eps.emm.cp_backoff_cap",
                "nas-eps.emm.restrict_ec_cap",
                "nas-eps.emm.v2x_pc5_cap",
                "nas-eps.emm.multiple_drb_cap"
        });
        current++;

        if ((current - (offset + 2)) >= length) {
            return offset + 2 + length;
        }
        decodeBitOctet(node, flatFields, current, reader.u8(current), new String[]{
                "nas-eps.emm.rpr_cap",
                "nas-eps.emm.piv_cap",
                "nas-eps.emm.ncr_cap",
                "nas-eps.emm.v2x_nr_pc5_cap",
                "nas-eps.emm.up_mt_edt_cap",
                "nas-eps.emm.cp_mt_edt_cap",
                "nas-eps.emm.wsua_cap",
                "nas-eps.emm.racs_cap"
        });
        current++;

        if ((current - (offset + 2)) >= length) {
            return offset + 2 + length;
        }
        decodeBitOctet(node, flatFields, current, reader.u8(current), new String[]{
                "nas-eps.emm.mint_eps_cap",
                "nas-eps.emm.ohr_cp_ciot_cap",
                "nas-eps.emm.sfso_cap",
                "nas-eps.emm.atuc_cap",
                "nas-eps.emm.rclin_cap",
                "nas-eps.emm.edc_cap",
                "nas-eps.emm.ptcc_cap",
                "nas-eps.emm.pr_cap"
        });
        return offset + 2 + length;
    }

    private int decodePsiStatusTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode,
            String nodeName,
            String fieldPrefix
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 2 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode(nodeName, "", offset, 2 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        decodePsiOctet(node, flatFields, fieldPrefix, reader.u8(offset + 2), 0, offset + 2);
        decodePsiOctet(node, flatFields, fieldPrefix, reader.u8(offset + 3), 8, offset + 3);
        return offset + 2 + length;
    }

    private int decodeUeStatusTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("UE status", "", offset, 2 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        int octet = reader.u8(offset + 2);
        addField(node, flatFields, "nas-5gs.mm.n1_mode_reg_b1", Integer.toString((octet >>> 1) & 0x01), offset + 2, 1);
        addField(node, flatFields, "nas-5gs.mm.s1_mode_reg_b0", Integer.toString(octet & 0x01), offset + 2, 1);
        return offset + 2 + length;
    }

    private void decodeSnssai(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (length < 1 || reader.remaining(offset) < length) {
            return;
        }
        addField(node, flatFields, "nas-5gs.mm.sst", Integer.toString(reader.u8(offset)), offset, 1);
        if (length == 1) {
            return;
        }
        int current = offset + 1;
        if (length > 2) {
            addField(node, flatFields, "nas-5gs.mm.mm_sd", Integer.toString((reader.u8(current) << 16) | (reader.u8(current + 1) << 8) | reader.u8(current + 2)), current, 3);
            current += 3;
            if (length == 4) {
                return;
            }
        }
        addField(node, flatFields, "nas-5gs.mm.mapped_hplmn_sst", Integer.toString(reader.u8(current)), current, 1);
        if (length == 2 || length == 5) {
            return;
        }
        addField(node, flatFields, "nas-5gs.mm.mapped_hplmn_ssd", Integer.toString((reader.u8(current + 1) << 16) | (reader.u8(current + 2) << 8) | reader.u8(current + 3)), current + 1, 3);
    }

    private int decodeLadnIndicationTlvE(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 2) {
            return offset;
        }
        int length = reader.u16(offset + 1);
        if (reader.remaining(offset + 3) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("LADN indication", "", offset, 3 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 2));
        int current = offset + 3;
        int end = current + length;
        int index = 1;
        while (current < end && reader.remaining(current) > 0) {
            int dnnLength = reader.u8(current);
            if (current + 1 + dnnLength > end || reader.remaining(current + 1) < dnnLength) {
                break;
            }
            DecodedFieldNode dnnNode = new DecodedFieldNode("LADN DNN value " + index, "", current, 1 + dnnLength);
            node.addChild(dnnNode);
            dnnNode.addChild(new DecodedFieldNode("Length", Integer.toString(dnnLength), current, 1));
            addField(
                    dnnNode,
                    flatFields,
                    "nas-5gs.cmn.dnn",
                    decodeDnn(reader.slice(current + 1, dnnLength).toByteArray()),
                    current + 1,
                    dnnLength
            );
            current += 1 + dnnLength;
            index++;
        }
        return offset + 3 + length;
    }

    private int decodeEpsNasMessageContainerTlvE(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 2) {
            return offset;
        }
        int length = reader.u16(offset + 1);
        if (reader.remaining(offset + 3) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("EPS NAS message container", "", offset, 3 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 2));
        if (length > 0) {
            decodeEmbeddedEpsNasMessage(reader, offset + 3, length, flatFields, node);
        }
        return offset + 3 + length;
    }

    private int decodePayloadContainerTlvE(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 2) {
            return offset;
        }
        int length = reader.u16(offset + 1);
        if (reader.remaining(offset + 3) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("Payload container", "", offset, 3 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 2));
        if (length > 0) {
            int payloadType = parseIntOrDefault(flatFields.get("nas-5gs.mm.pld_cont_type"), -1);
            decodePayloadContainerValue(reader, offset + 3, length, payloadType, flatFields, node);
        }
        return offset + 3 + length;
    }

    private int decodeUeUsageSettingTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("UE's usage setting", "", offset, 2 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        addField(node, flatFields, "nas-5gs.mm.ue_usage_setting", Integer.toString(reader.u8(offset + 2) & 0x01), offset + 2, 1);
        return offset + 2 + length;
    }

    private int decodeRequestedDrxParametersTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("Requested DRX parameters", "", offset, 2 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        addField(node, flatFields, "nas-5gs.mm.drx_value", Integer.toString(reader.u8(offset + 2) & 0x0f), offset + 2, 1);
        return offset + 2 + length;
    }

    private void decodeEmbeddedEpsNasMessage(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (length < 2 || reader.remaining(offset) < length) {
            node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset, length).toByteArray()), offset, length));
            return;
        }

        int elementId = reader.u8(offset);
        int valueLength = reader.u8(offset + 1);
        if (valueLength >= 0 && valueLength == length - 2) {
            addField(node, flatFields, "nas-eps.emm.elem_id", Integer.toString(elementId), offset, 1);
            addField(node, flatFields, "gsm_a.len", Integer.toString(valueLength), offset + 1, 1);
            if (valueLength > 0) {
                addField(node, flatFields, "nas-eps.emm.res", hex(reader.slice(offset + 2, valueLength).toByteArray()), offset + 2, valueLength);
            }
            return;
        }

        node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset, length).toByteArray()), offset, length));
    }

    private void decodePayloadContainerValue(
            Nas5gsIeReader reader,
            int offset,
            int length,
            int payloadType,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        switch (payloadType) {
            case 0x01 -> decodeN1SmInformation(reader, offset, length, flatFields, node);
            case 0x04 -> decodeSorTransparentContainer(reader, offset, length, flatFields, node);
            case 0x09 -> decodeServiceLevelAaContainer(reader, offset, length, flatFields, node);
            case 0x0a -> decodeEventNotificationContainer(reader, offset, length, node);
            case 0x0f -> decodeMultiplePayloadContainer(reader, offset, length, flatFields, node);
            default -> {
                if (payloadType >= 0) {
                    node.addChild(new DecodedFieldNode(
                            "Payload container type name",
                            payloadContainerTypeName(payloadType),
                            offset,
                            0
                    ));
                }
                node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset, length).toByteArray()), offset, length));
            }
        }
    }

    private void decodeN1SmInformation(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (length < 4 || reader.remaining(offset) < length) {
            node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset, Math.max(0, length)).toByteArray()), offset, Math.max(0, length)));
            return;
        }

        int epd = reader.u8(offset);
        addField(node, flatFields, "nas-5gs.epd", Integer.toString(epd), offset, 1);
        if (epd != 0x2e) {
            node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset + 1, length - 1).toByteArray()), offset + 1, length - 1));
            return;
        }

        addField(node, flatFields, "nas-5gs.pdu_session_id", Integer.toString(reader.u8(offset + 1)), offset + 1, 1);
        addField(node, flatFields, "nas-5gs.proc_trans_id", Integer.toString(reader.u8(offset + 2)), offset + 2, 1);
        int messageType = reader.u8(offset + 3);
        addField(node, flatFields, "nas-5gs.sm.message_type", Integer.toString(messageType), offset + 3, 1);
        String messageName = nas5gsSmMessageTypeName(messageType);
        if (messageName != null) {
            node.addChild(new DecodedFieldNode("nas-5gs.sm.message_type_name", messageName, offset + 3, 1));
        }
        int bodyOffset = offset + 4;
        int bodyLength = length - 4;
        switch (messageType) {
            case 0xc1 -> decodePduSessionEstablishmentRequest(reader, bodyOffset, bodyLength, flatFields, node);
            case 0xc2 -> decodePduSessionEstablishmentAccept(reader, bodyOffset, bodyLength, flatFields, node);
            case 0xc3 -> decodePduSessionEstablishmentReject(reader, bodyOffset, bodyLength, flatFields, node);
            case 0xd6 -> decode5gsmStatus(reader, bodyOffset, bodyLength, flatFields, node);
            default -> {
                if (bodyLength > 0) {
                    node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(bodyOffset, bodyLength).toByteArray()), bodyOffset, bodyLength));
                }
            }
        }
    }

    private void decodeSorTransparentContainer(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (length < 1 || reader.remaining(offset) < length) {
            node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset, Math.max(0, length)).toByteArray()), offset, Math.max(0, length)));
            return;
        }

        int current = offset;
        int octet = reader.u8(current);
        int dataType = octet & 0x01;
        if (dataType == 0) {
            addField(node, flatFields, "nas-5gs.sor_hdr0.ap", Integer.toString((octet >>> 4) & 0x01), current, 1);
            addField(node, flatFields, "nas-5gs.sor_hdr0.ack", Integer.toString((octet >>> 3) & 0x01), current, 1);
            addField(node, flatFields, "nas-5gs.sor_hdr0.list_type", Integer.toString((octet >>> 2) & 0x01), current, 1);
            addField(node, flatFields, "nas-5gs.sor_hdr0.list_ind", Integer.toString((octet >>> 1) & 0x01), current, 1);
            addField(node, flatFields, "nas-5gs.sor.sor_data_type", Integer.toString(dataType), current, 1);
            current++;

            if (current + 18 > offset + length || reader.remaining(current) < 18) {
                return;
            }
            addField(node, flatFields, "nas-5gs.mm.sor_mac_iausf", hex(reader.slice(current, 16).toByteArray()), current, 16);
            current += 16;
            addField(node, flatFields, "nas-5gs.mm.counter_sor", Integer.toString(reader.u16(current)), current, 2);
            current += 2;

            int listType = (octet >>> 2) & 0x01;
            if (listType == 0) {
                int remaining = (offset + length) - current;
                if (remaining > 0) {
                    addField(node, flatFields, "nas-5gs.mm.sor_sec_pkt", hex(reader.slice(current, remaining).toByteArray()), current, remaining);
                }
                return;
            }

            int ap = (octet >>> 4) & 0x01;
            if (ap == 1) {
                current = decodeSorAdditionalParameters(reader, current, offset + length, flatFields, node);
            } else {
                current = decodeSorPlmnAccessList(reader, current, offset + length, flatFields, node);
            }
            int remaining = (offset + length) - current;
            if (remaining > 0) {
                node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(current, remaining).toByteArray()), current, remaining));
            }
            return;
        }

        addField(node, flatFields, "nas-5gs.sor.msssnpnsils", Integer.toString((octet >>> 3) & 0x01), current, 1);
        addField(node, flatFields, "nas-5gs.sor.mssnpnsi", Integer.toString((octet >>> 2) & 0x01), current, 1);
        addField(node, flatFields, "nas-5gs.sor.mssi", Integer.toString((octet >>> 1) & 0x01), current, 1);
        addField(node, flatFields, "nas-5gs.sor.sor_data_type", Integer.toString(dataType), current, 1);
        current++;
        if (current + 16 <= offset + length && reader.remaining(current) >= 16) {
            addField(node, flatFields, "nas-5gs.mm.sor_mac_iue", hex(reader.slice(current, 16).toByteArray()), current, 16);
            current += 16;
        }
        int remaining = (offset + length) - current;
        if (remaining > 0) {
            node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(current, remaining).toByteArray()), current, remaining));
        }
    }

    private void decodeServiceLevelAaContainer(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        int current = offset;
        int index = 1;
        int end = offset + length;
        while (current < end && reader.remaining(current) > 0) {
            int paramStart = current;
            int rawType = reader.u8(current);
            int type = rawType;
            int paramLength;
            int lenOffset = -1;
            int lenFieldLength = 0;
            current++;

            if ((rawType & 0x80) == 0x80) {
                paramLength = 0;
                type = rawType & 0xf0;
            } else {
                if ((rawType & 0xf0) == 0x70) {
                    if (current + 2 > end || reader.remaining(current) < 2) {
                        break;
                    }
                    lenOffset = current;
                    lenFieldLength = 2;
                    paramLength = reader.u16(current);
                    current += 2;
                } else {
                    if (current >= end || reader.remaining(current) < 1) {
                        break;
                    }
                    lenOffset = current;
                    lenFieldLength = 1;
                    paramLength = reader.u8(current);
                    current++;
                }
            }

            if (paramLength < 0 || current + paramLength > end || reader.remaining(current) < paramLength) {
                break;
            }

            DecodedFieldNode paramNode = new DecodedFieldNode("Service-level-AA parameter " + index, "", paramStart, current + paramLength - paramStart);
            node.addChild(paramNode);
            addField(paramNode, flatFields, "nas-5gs.cmn.service_level_aa_param.type", String.format(Locale.ROOT, "0x%02x", type), paramStart, 1);
            if (lenFieldLength > 0) {
                paramNode.addChild(new DecodedFieldNode("nas-5gs.cmn.service_level_aa_param.len", Integer.toString(paramLength), lenOffset, lenFieldLength));
            }

            decodeServiceLevelAaParameter(reader, current, paramLength, rawType, type, flatFields, paramNode);
            current += paramLength;
            index++;
        }
    }

    private void decodeServiceLevelAaParameter(
            Nas5gsIeReader reader,
            int offset,
            int length,
            int rawType,
            int type,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        switch (type) {
            case 0x10 -> {
                if (length > 0) {
                    addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.device_id", decodeUtf8(reader.slice(offset, length).toByteArray()), offset, length);
                }
            }
            case 0x20 -> {
                if (length < 1) {
                    return;
                }
                int addressType = reader.u8(offset);
                addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.addr.type", Integer.toString(addressType), offset, 1);
                if (addressType == 1 && length >= 5) {
                    addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.addr.ipv4", decodeIpv4(reader.slice(offset + 1, 4).toByteArray()), offset + 1, 4);
                } else if (addressType == 2 && length >= 17) {
                    addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.addr.ipv6", decodeIpv6(reader.slice(offset + 1, 16).toByteArray()), offset + 1, 16);
                } else if (addressType == 3 && length >= 21) {
                    addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.addr.ipv4", decodeIpv4(reader.slice(offset + 1, 4).toByteArray()), offset + 1, 4);
                    addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.addr.ipv6", decodeIpv6(reader.slice(offset + 5, 16).toByteArray()), offset + 5, 16);
                } else if (addressType == 4 && length >= 2) {
                    addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.addr.fqdn", decodeDnn(reader.slice(offset + 1, length - 1).toByteArray()), offset + 1, length - 1);
                } else if (length > 1) {
                    node.addChild(new DecodedFieldNode("Value", hex(reader.slice(offset + 1, length - 1).toByteArray()), offset + 1, length - 1));
                }
            }
            case 0x30 -> {
                if (length < 1) {
                    return;
                }
                int octet = reader.u8(offset);
                addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.response.c2ar", Integer.toString((octet >>> 2) & 0x03), offset, 1);
                addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.response.slar", Integer.toString(octet & 0x03), offset, 1);
            }
            case 0x40 -> {
                if (length > 0) {
                    addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.payload_type", Integer.toString(reader.u8(offset)), offset, 1);
                }
            }
            case 0x50 -> {
                if (length > 0) {
                    addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.service_status_indication.uas", Integer.toString(reader.u8(offset) & 0x01), offset, 1);
                }
            }
            case 0x70 -> {
                if (length > 0) {
                    addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.payload", hex(reader.slice(offset, length).toByteArray()), offset, length);
                }
            }
            case 0xa0 -> addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.pending_indication.slapi", Integer.toString(rawType & 0x01), offset - 1, 1);
            default -> {
                if (length > 0) {
                    addField(node, flatFields, "nas-5gs.cmn.service_level_aa_param.unknown", hex(reader.slice(offset, length).toByteArray()), offset, length);
                }
            }
        }
    }

    private void decodeEventNotificationContainer(
            Nas5gsIeReader reader,
            int offset,
            int length,
            DecodedFieldNode node
    ) {
        if (length < 1 || reader.remaining(offset) < length) {
            node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset, Math.max(0, length)).toByteArray()), offset, Math.max(0, length)));
            return;
        }

        int current = offset;
        int eventCount = reader.u8(current);
        node.addChild(new DecodedFieldNode("Number of event notification indicators", Integer.toString(eventCount), current, 1));
        current++;

        for (int i = 0; i < eventCount && current < offset + length; i++) {
            if (reader.remaining(current) < 2) {
                break;
            }
            int type = reader.u8(current);
            int eventLength = reader.u8(current + 1);
            if (reader.remaining(current + 2) < eventLength || current + 2 + eventLength > offset + length) {
                break;
            }
            DecodedFieldNode eventNode = new DecodedFieldNode("Event notification indicator " + (i + 1), "", current, 2 + eventLength);
            node.addChild(eventNode);
            eventNode.addChild(new DecodedFieldNode("Type", Integer.toString(type), current, 1));
            eventNode.addChild(new DecodedFieldNode("Length", Integer.toString(eventLength), current + 1, 1));
            if (eventLength > 0) {
                eventNode.addChild(new DecodedFieldNode("Value", hex(reader.slice(current + 2, eventLength).toByteArray()), current + 2, eventLength));
            }
            current += 2 + eventLength;
        }
    }

    private void decodeMultiplePayloadContainer(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (length < 1 || reader.remaining(offset) < length) {
            node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset, Math.max(0, length)).toByteArray()), offset, Math.max(0, length)));
            return;
        }

        int current = offset;
        int entryCount = reader.u8(current);
        node.addChild(new DecodedFieldNode("Number of payload container entries", Integer.toString(entryCount), current, 1));
        current++;

        for (int i = 0; i < entryCount && current < offset + length; i++) {
            if (reader.remaining(current) < 3) {
                break;
            }
            int payloadLength = reader.u16(current);
            int entryStart = current + 2;
            if (payloadLength < 1 || reader.remaining(entryStart) < payloadLength || entryStart + payloadLength > offset + length) {
                break;
            }

            DecodedFieldNode entryNode = new DecodedFieldNode("Payload container entry " + (i + 1), "", current, 2 + payloadLength);
            node.addChild(entryNode);
            entryNode.addChild(new DecodedFieldNode("Payload container entry length", Integer.toString(payloadLength), current, 2));
            current = entryStart;

            int typeAndCount = reader.u8(current);
            int optIeCount = (typeAndCount >>> 4) & 0x0f;
            int nestedPayloadType = typeAndCount & 0x0f;
            entryNode.addChild(new DecodedFieldNode("Number of optional IEs", Integer.toString(optIeCount), current, 1));
            entryNode.addChild(new DecodedFieldNode("Payload container type", Integer.toString(nestedPayloadType), current, 1));
            current++;

            int entryEnd = entryStart + payloadLength;
            for (int j = 0; j < optIeCount && current < entryEnd; j++) {
                if (reader.remaining(current) < 2) {
                    break;
                }
                int optIeType = reader.u8(current);
                int optIeLength = reader.u8(current + 1);
                if (reader.remaining(current + 2) < optIeLength || current + 2 + optIeLength > entryEnd) {
                    break;
                }
                DecodedFieldNode optIeNode = new DecodedFieldNode("Optional IE " + (j + 1), "", current, 2 + optIeLength);
                entryNode.addChild(optIeNode);
                optIeNode.addChild(new DecodedFieldNode("Type", String.format(Locale.ROOT, "0x%02x", optIeType), current, 1));
                optIeNode.addChild(new DecodedFieldNode("Length", Integer.toString(optIeLength), current + 1, 1));
                decodePayloadContainerOptionalIe(reader, current + 2, optIeLength, optIeType, flatFields, optIeNode);
                current += 2 + optIeLength;
            }

            int remainingPayload = entryEnd - current;
            if (remainingPayload > 0) {
                decodePayloadContainerValue(reader, current, remainingPayload, nestedPayloadType, flatFields, entryNode);
            }
            current = entryEnd;
        }
    }

    private void decodePayloadContainerOptionalIe(
            Nas5gsIeReader reader,
            int offset,
            int length,
            int optIeType,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        switch (optIeType) {
            case 0x12, 0x59 -> addField(node, flatFields, "nas-5gs.pdu_session_id", Integer.toString(reader.u8(offset) & 0x0f), offset, Math.min(1, length));
            case 0x22 -> decodeSnssai(reader, offset, length, flatFields, node);
            case 0x24 -> {
                if (length > 0) {
                    node.addChild(new DecodedFieldNode("Value", hex(reader.slice(offset, length).toByteArray()), offset, length));
                }
            }
            case 0x25 -> {
                if (length > 0) {
                    addField(node, flatFields, "nas-5gs.cmn.dnn", decodeDnn(reader.slice(offset, length).toByteArray()), offset, length);
                }
            }
            case 0x37 -> {
                if (length > 0) {
                    addField(node, flatFields, "gprs_timer_3.timer_value", Integer.toString(reader.u8(offset)), offset, 1);
                }
            }
            case 0x58 -> {
                if (length > 0) {
                    addField(node, flatFields, "nas-5gs.mm.5gmm_cause", Integer.toString(reader.u8(offset)), offset, 1);
                }
            }
            case 0x80 -> {
                if (length > 0) {
                    addField(node, flatFields, "nas-5gs.mm.req_type", Integer.toString(reader.u8(offset) & 0x07), offset, 1);
                }
            }
            case 0xa0 -> {
                if (length > 0) {
                    addField(node, flatFields, "nas-5gs.mm.ma_pdu_session_info_value", Integer.toString(reader.u8(offset)), offset, 1);
                }
            }
            case 0xf0 -> {
                if (length > 0) {
                    addField(node, flatFields, "nas-eps.esm.rel_assist_ind.ddx", Integer.toString(reader.u8(offset) & 0x03), offset, 1);
                }
            }
            default -> {
                if (length > 0) {
                    node.addChild(new DecodedFieldNode("Value", hex(reader.slice(offset, length).toByteArray()), offset, length));
                }
            }
        }
    }

    private void decode5gsmStatus(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (length < 1 || reader.remaining(offset) < 1) {
            return;
        }
        addField(node, flatFields, "nas-5gs.sm.5gsm_cause", Integer.toString(reader.u8(offset)), offset, 1);
        if (length > 1) {
            node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset + 1, length - 1).toByteArray()), offset + 1, length - 1));
        }
    }

    private void decodePduSessionEstablishmentRequest(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (length < 2 || reader.remaining(offset) < 2) {
            if (length > 0) {
                node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset, length).toByteArray()), offset, length));
            }
            return;
        }

        addField(node, flatFields, "nas-5gs.sm.int_prot_max_data_rate_ul", Integer.toString(reader.u8(offset)), offset, 1);
        addField(node, flatFields, "nas-5gs.sm.int_prot_max_data_rate_dl", Integer.toString(reader.u8(offset + 1)), offset + 1, 1);

        int current = offset + 2;
        int end = offset + length;
        while (current < end && reader.remaining(current) > 0) {
            int iei = reader.u8(current);
            if ((iei & 0xf0) == 0x90) {
                addField(node, flatFields, "nas-5gs.sm.pdu_session_type", Integer.toString(iei & 0x07), current, 1);
                current++;
                continue;
            }
            if ((iei & 0xf0) == 0xa0) {
                addField(node, flatFields, "nas-5gs.sm.sc_mode", Integer.toString(iei & 0x07), current, 1);
                current++;
                continue;
            }
            if ((iei & 0xf0) == 0xb0) {
                addField(node, flatFields, "nas-5gs.sm.apsr", Integer.toString(iei & 0x01), current, 1);
                current++;
                continue;
            }

            switch (iei) {
                case 0x28 -> current = decode5gsmCapabilityTlv(reader, current, flatFields, node);
                case 0x55 -> current = decodeMaxSupportedPacketFiltersTv(reader, current, flatFields, node);
                case 0x39 -> current = decodeSmPduDnRequestContainerTlv(reader, current, flatFields, node);
                case 0x7b -> current = decodeGenericTlvE(reader, current, "Extended protocol configuration options", node);
                case 0x66 -> current = decodeIpHeaderCompressionConfigurationTlv(reader, current, flatFields, node);
                case 0x6e -> current = decodeDsTtEthernetPortMacAddressTlv(reader, current, flatFields, node);
                case 0x6f -> current = decodeUeDsTtResidenceTimeTlv(reader, current, flatFields, node);
                case 0x74 -> current = decodePortManagementInformationContainerTlvE(reader, current, flatFields, node);
                case 0x1f -> current = decodeEthernetHeaderCompressionConfigurationTlv(reader, current, flatFields, node);
                case 0x29 -> current = decodeSuggestedInterfaceIdentifierTlv(reader, current, flatFields, node);
                case 0x72 -> current = decodeNestedServiceLevelAaContainerTlvE(reader, current, flatFields, node);
                case 0x70 -> current = decodeRequestedMbsContainerTlvE(reader, current, node);
                case 0x34 -> current = decodePduSessionPairIdTlv(reader, current, flatFields, node);
                case 0x35 -> current = decodeRsnTlv(reader, current, flatFields, node);
                case 0x36 -> current = decodeGenericTlv(reader, current, "URSP rule enforcement reports", node);
                default -> {
                    node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(current, end - current).toByteArray()), current, end - current));
                    return;
                }
            }
            if (current <= offset + 1) {
                return;
            }
        }
    }

    private int decode5gsmCapabilityTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode capabilityNode = new DecodedFieldNode("5GSM capability", "", offset, 2 + length);
        node.addChild(capabilityNode);
        capabilityNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        int current = offset + 2;
        if (length >= 1) {
            int octet = reader.u8(current);
            addField(capabilityNode, flatFields, "nas-5gs.sm.tpmic", Integer.toString((octet >>> 7) & 0x01), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.sm.atsss_st", Integer.toString((octet >>> 3) & 0x0f), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.sm.ept_s1", Integer.toString((octet >>> 2) & 0x01), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.sm.mh6_pdu", Integer.toString((octet >>> 1) & 0x01), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.sm.rqos", Integer.toString(octet & 0x01), current, 1);
            current++;
        }
        if (length >= 2) {
            int octet = reader.u8(current);
            addField(capabilityNode, flatFields, "nas-5gs.sm.mpquic_ip", Integer.toString((octet >>> 7) & 0x01), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.sm.mpquic_udp", Integer.toString((octet >>> 6) & 0x01), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.sm.mptcp", Integer.toString((octet >>> 5) & 0x01), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.sm.atsss_ll", Integer.toString((octet >>> 3) & 0x03), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.rtpmmii", Integer.toString((octet >>> 2) & 0x01), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.sm.sdnaepc", Integer.toString((octet >>> 1) & 0x01), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.sm.apmqf", Integer.toString(octet & 0x01), current, 1);
            current++;
        }
        if (length >= 3) {
            int octet = reader.u8(current);
            addField(capabilityNode, flatFields, "nas-5gs.sm.e8pcpdei", Integer.toString((octet >>> 1) & 0x01), current, 1);
            addField(capabilityNode, flatFields, "nas-5gs.sm.mpquic_e", Integer.toString(octet & 0x01), current, 1);
        }
        return offset + 2 + length;
    }

    private int decodeMaxSupportedPacketFiltersTv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset) < 3) {
            return offset;
        }
        DecodedFieldNode filtersNode = new DecodedFieldNode("Maximum number of supported packet filter", "", offset, 3);
        node.addChild(filtersNode);
        int value = reader.u16(offset + 1);
        addField(filtersNode, flatFields, "nas-5gs.sm.max_nb_sup_pkt_flt.nb", Integer.toString((value >>> 6) & 0x03ff), offset + 1, 2);
        addField(filtersNode, flatFields, "nas-5gs.sm.max_nb_sup_pkt_flt.spare", Integer.toString(value & 0x3f), offset + 1, 2);
        return offset + 3;
    }

    private int decodeSmPduDnRequestContainerTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode containerNode = new DecodedFieldNode("SM PDU DN request container", "", offset, 2 + length);
        node.addChild(containerNode);
        containerNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        if (length > 0) {
            addField(containerNode, flatFields, "nas-5gs.sm.dm_spec_id", decodeUtf8(reader.slice(offset + 2, length).toByteArray()), offset + 2, length);
        }
        return offset + 2 + length;
    }

    private int decodeIpHeaderCompressionConfigurationTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 3 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode configNode = new DecodedFieldNode("Header compression configuration", "", offset, 2 + length);
        node.addChild(configNode);
        configNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        int current = offset + 2;
        int profiles = reader.u8(current);
        addField(configNode, flatFields, "nas-5gs.sm.ip_hdr_comp_config.p0104", Integer.toString((profiles >>> 6) & 0x01), current, 1);
        addField(configNode, flatFields, "nas-5gs.sm.ip_hdr_comp_config.p0103", Integer.toString((profiles >>> 5) & 0x01), current, 1);
        addField(configNode, flatFields, "nas-5gs.sm.ip_hdr_comp_config.p0102", Integer.toString((profiles >>> 4) & 0x01), current, 1);
        addField(configNode, flatFields, "nas-5gs.sm.ip_hdr_comp_config.p0006", Integer.toString((profiles >>> 3) & 0x01), current, 1);
        addField(configNode, flatFields, "nas-5gs.sm.ip_hdr_comp_config.p0004", Integer.toString((profiles >>> 2) & 0x01), current, 1);
        addField(configNode, flatFields, "nas-5gs.sm.ip_hdr_comp_config.p0003", Integer.toString((profiles >>> 1) & 0x01), current, 1);
        addField(configNode, flatFields, "nas-5gs.sm.ip_hdr_comp_config.p0002", Integer.toString(profiles & 0x01), current, 1);
        current++;
        addField(configNode, flatFields, "nas-5gs.sm.ip_hdr_comp_config.max_cid", Integer.toString(reader.u16(current)), current, 2);
        current += 2;
        if (current < offset + 2 + length) {
            addField(configNode, flatFields, "nas-5gs.sm.ip_hdr_comp_config.add_hdr_compr_cxt_setup_params_type", Integer.toString(reader.u8(current)), current, 1);
            current++;
        }
        int remaining = (offset + 2 + length) - current;
        if (remaining > 0) {
            addField(configNode, flatFields, "nas-5gs.sm.ip_hdr_comp_config.add_hdr_compr_cxt_setup_params_cont", hex(reader.slice(current, remaining).toByteArray()), current, remaining);
        }
        return offset + 2 + length;
    }

    private int decodeDsTtEthernetPortMacAddressTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode macNode = new DecodedFieldNode("DS-TT Ethernet port MAC address", "", offset, 2 + length);
        node.addChild(macNode);
        macNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        if (length > 0) {
            addField(macNode, flatFields, "nas-5gs.sm.ds_tt_eth_port_mac_addr", decodeMac(reader.slice(offset + 2, length).toByteArray()), offset + 2, length);
        }
        return offset + 2 + length;
    }

    private int decodeUeDsTtResidenceTimeTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode residenceNode = new DecodedFieldNode("UE-DS-TT residence time", "", offset, 2 + length);
        node.addChild(residenceNode);
        residenceNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        if (length > 0) {
            addField(residenceNode, flatFields, "nas-5gs.sm.ue_ds_tt_residence_time", hex(reader.slice(offset + 2, length).toByteArray()), offset + 2, length);
        }
        return offset + 2 + length;
    }

    private int decodePortManagementInformationContainerTlvE(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 2) {
            return offset;
        }
        int length = reader.u16(offset + 1);
        if (reader.remaining(offset + 3) < length) {
            return offset;
        }
        DecodedFieldNode containerNode = new DecodedFieldNode("Port management information container", "", offset, 3 + length);
        node.addChild(containerNode);
        containerNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 2));
        if (length > 0) {
            addField(containerNode, flatFields, "nas-5gs.sm.port_mgmt_info_cont", hex(reader.slice(offset + 3, length).toByteArray()), offset + 3, length);
        }
        return offset + 3 + length;
    }

    private int decodeNestedServiceLevelAaContainerTlvE(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 2) {
            return offset;
        }
        int length = reader.u16(offset + 1);
        if (reader.remaining(offset + 3) < length) {
            return offset;
        }
        DecodedFieldNode containerNode = new DecodedFieldNode("Service-level-AA container", "", offset, 3 + length);
        node.addChild(containerNode);
        containerNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 2));
        if (length > 0) {
            decodeServiceLevelAaContainer(reader, offset + 3, length, flatFields, containerNode);
        }
        return offset + 3 + length;
    }

    private int decodeRequestedMbsContainerTlvE(
            Nas5gsIeReader reader,
            int offset,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 2) {
            return offset;
        }
        int length = reader.u16(offset + 1);
        if (reader.remaining(offset + 3) < length) {
            return offset;
        }
        DecodedFieldNode containerNode = new DecodedFieldNode("Requested MBS container", "", offset, 3 + length);
        node.addChild(containerNode);
        containerNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 2));
        if (length > 0) {
            containerNode.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset + 3, length).toByteArray()), offset + 3, length));
        }
        return offset + 3 + length;
    }

    private int decodeEthernetHeaderCompressionConfigurationTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode configNode = new DecodedFieldNode("Ethernet header compression configuration", "", offset, 2 + length);
        node.addChild(configNode);
        configNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        addField(configNode, flatFields, "nas-5gs.sm.eth_hdr_comp_config.cid_len", Integer.toString(reader.u8(offset + 2) & 0x03), offset + 2, 1);
        return offset + 2 + length;
    }

    private int decodeSuggestedInterfaceIdentifierTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode pduAddressNode = new DecodedFieldNode("Suggested interface identifier", "", offset, 2 + length);
        node.addChild(pduAddressNode);
        pduAddressNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        int current = offset + 2;
        int firstOctet = reader.u8(current);
        addField(pduAddressNode, flatFields, "nas-5gs.sm.si6lla", Integer.toString((firstOctet >>> 3) & 0x01), current, 1);
        int pduType = firstOctet & 0x07;
        addField(pduAddressNode, flatFields, "nas-5gs.sm.pdu_ses_type", Integer.toString(pduType), current, 1);
        current++;
        if (pduType == 1 && current + 4 <= offset + 2 + length) {
            addField(pduAddressNode, flatFields, "nas-5gs.sm.pdu_addr_inf_ipv4", decodeIpv4(reader.slice(current, 4).toByteArray()), current, 4);
            current += 4;
        } else if (pduType == 2 && current + 8 <= offset + 2 + length) {
            addField(pduAddressNode, flatFields, "nas-5gs.sm.pdu_addr_inf_ipv6", decodeInterfaceIdIpv6(reader.slice(current, 8).toByteArray()), current, 8);
            current += 8;
        } else if (pduType == 3 && current + 12 <= offset + 2 + length) {
            addField(pduAddressNode, flatFields, "nas-5gs.sm.pdu_addr_inf_ipv6", decodeInterfaceIdIpv6(reader.slice(current, 8).toByteArray()), current, 8);
            current += 8;
            addField(pduAddressNode, flatFields, "nas-5gs.sm.pdu_addr_inf_ipv4", decodeIpv4(reader.slice(current, 4).toByteArray()), current, 4);
            current += 4;
        }
        if (((firstOctet >>> 3) & 0x01) == 1 && current + 16 <= offset + 2 + length) {
            addField(pduAddressNode, flatFields, "nas-5gs.sm.smf_ipv6_lla", decodeIpv6(reader.slice(current, 16).toByteArray()), current, 16);
        }
        return offset + 2 + length;
    }

    private int decodePduSessionPairIdTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode pairNode = new DecodedFieldNode("PDU session pair ID", "", offset, 2 + length);
        node.addChild(pairNode);
        pairNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        addField(pairNode, flatFields, "nas-5gs.sm.pdu_session_pair_id", Integer.toString(reader.u8(offset + 2)), offset + 2, 1);
        return offset + 2 + length;
    }

    private int decodeRsnTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode rsnNode = new DecodedFieldNode("RSN", "", offset, 2 + length);
        node.addChild(rsnNode);
        rsnNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        addField(rsnNode, flatFields, "nas-5gs.sm.rsn", Integer.toString(reader.u8(offset + 2)), offset + 2, 1);
        return offset + 2 + length;
    }

    private void decodePduSessionEstablishmentAccept(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (length < 4 || reader.remaining(offset) < 4) {
            if (length > 0) {
                node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset, length).toByteArray()), offset, length));
            }
            return;
        }

        int firstOctet = reader.u8(offset);
        addField(node, flatFields, "nas-5gs.sm.sel_sc_mode", Integer.toString((firstOctet >>> 4) & 0x07), offset, 1);
        addField(node, flatFields, "nas-5gs.sm.pdu_session_type", Integer.toString(firstOctet & 0x07), offset, 1);

        int current = offset + 1;
        if (reader.remaining(current) < 2) {
            return;
        }
        int qosRulesLength = reader.u16(current);
        if (reader.remaining(current + 2) < qosRulesLength) {
            return;
        }
        DecodedFieldNode qosRulesNode = new DecodedFieldNode("Authorized QoS rules", "", current, 2 + qosRulesLength);
        node.addChild(qosRulesNode);
        qosRulesNode.addChild(new DecodedFieldNode("Length", Integer.toString(qosRulesLength), current, 2));
        if (qosRulesLength > 0) {
            decodeQosRules(reader, current + 2, qosRulesLength, flatFields, qosRulesNode);
        }
        current += 2 + qosRulesLength;

        if (reader.remaining(current) < 1) {
            return;
        }
        int sessionAmbrLength = reader.u8(current);
        if (reader.remaining(current + 1) < sessionAmbrLength) {
            return;
        }
        DecodedFieldNode sessionAmbrNode = new DecodedFieldNode("Session AMBR", "", current, 1 + sessionAmbrLength);
        node.addChild(sessionAmbrNode);
        sessionAmbrNode.addChild(new DecodedFieldNode("Length", Integer.toString(sessionAmbrLength), current, 1));
        if (sessionAmbrLength >= 6) {
            decodeSessionAmbr(reader, current + 1, flatFields, sessionAmbrNode);
        } else if (sessionAmbrLength > 0) {
            sessionAmbrNode.addChild(new DecodedFieldNode("Payload", hex(reader.slice(current + 1, sessionAmbrLength).toByteArray()), current + 1, sessionAmbrLength));
        }
        current += 1 + sessionAmbrLength;

        int end = offset + length;
        while (current < end && reader.remaining(current) > 0) {
            int iei = reader.u8(current);
            if ((iei & 0xf0) == 0x80) {
                addField(node, flatFields, "nas-5gs.sm.apsi", Integer.toString(iei & 0x01), current, 1);
                current++;
                continue;
            }
            if ((iei & 0xf0) == 0xc0) {
                addField(node, flatFields, "nas-5gs.sm.ctl_plane_only_ind", Integer.toString(iei & 0x01), current, 1);
                current++;
                continue;
            }
            switch (iei) {
                case 0x59 -> {
                    if (reader.remaining(current + 1) < 1) {
                        return;
                    }
                    addField(node, flatFields, "nas-5gs.sm.5gsm_cause", Integer.toString(reader.u8(current + 1)), current + 1, 1);
                    current += 2;
                }
                case 0x29 -> current = decodeSuggestedInterfaceIdentifierTlv(reader, current, flatFields, node);
                case 0x56 -> {
                    if (reader.remaining(current + 1) < 1) {
                        return;
                    }
                    addField(node, flatFields, "gprs_timer.timer_value", Integer.toString(reader.u8(current + 1)), current + 1, 1);
                    current += 2;
                }
                case 0x22 -> current = decodeRequestedNssaiTlv(reader, current, flatFields, node);
                case 0x75 -> current = decodeMappedEpsBearerContextsTlvE(reader, current, flatFields, node);
                case 0x78 -> current = decodeGenericTlvE(reader, current, "EAP message", node);
                case 0x79 -> current = decodeGenericTlvE(reader, current, "Authorized QoS flow descriptions", node);
                case 0x7b -> current = decodeGenericTlvE(reader, current, "Extended protocol configuration options", node);
                case 0x25 -> current = decodeDnnTlv(reader, current, flatFields, node);
                case 0x17 -> current = decode5gsmNetworkFeatureSupportTlv(reader, current, flatFields, node);
                case 0x18 -> current = decodeGenericTlv(reader, current, "Serving PLMN rate control", node);
                case 0x77 -> current = decodeGenericTlvE(reader, current, "ATSSS container", node);
                case 0x66 -> current = decodeIpHeaderCompressionConfigurationTlv(reader, current, flatFields, node);
                case 0x1f -> current = decodeEthernetHeaderCompressionConfigurationTlv(reader, current, flatFields, node);
                case 0x72 -> current = decodeNestedServiceLevelAaContainerTlvE(reader, current, flatFields, node);
                case 0x71 -> current = decodeGenericTlvE(reader, current, "Received MBS container", node);
                case 0x70 -> current = decodeGenericTlvE(reader, current, "N3QAI", node);
                case 0x73 -> current = decodeGenericTlvE(reader, current, "Protocol description", node);
                case 0x38 -> current = decodeEcnMarkingForL4sIndicationTlv(reader, current, flatFields, node);
                default -> {
                    node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(current, end - current).toByteArray()), current, end - current));
                    return;
                }
            }
        }
    }

    private void decodePduSessionEstablishmentReject(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (length < 1 || reader.remaining(offset) < 1) {
            return;
        }
        addField(node, flatFields, "nas-5gs.sm.5gsm_cause", Integer.toString(reader.u8(offset)), offset, 1);
        int current = offset + 1;
        int end = offset + length;
        while (current < end && reader.remaining(current) > 0) {
            int iei = reader.u8(current);
            if ((iei & 0xf0) == 0xf0) {
                addField(node, flatFields, "nas-5gs.sm.all_ssc_mode_b2", Integer.toString((iei >>> 2) & 0x01), current, 1);
                addField(node, flatFields, "nas-5gs.sm.all_ssc_mode_b1", Integer.toString((iei >>> 1) & 0x01), current, 1);
                addField(node, flatFields, "nas-5gs.sm.all_ssc_mode_b0", Integer.toString(iei & 0x01), current, 1);
                current++;
                continue;
            }
            switch (iei) {
                case 0x37 -> {
                    if (reader.remaining(current + 2) < 1) {
                        return;
                    }
                    addField(node, flatFields, "gprs_timer_3.timer_value", Integer.toString(reader.u8(current + 2)), current + 2, 1);
                    current += 3;
                }
                case 0x78 -> current = decodeGenericTlvE(reader, current, "EAP message", node);
                case 0x61 -> current = decode5gsmCongestionReattemptIndicatorTlv(reader, current, flatFields, node);
                case 0x7b -> current = decodeGenericTlvE(reader, current, "Extended protocol configuration options", node);
                case 0x1d -> current = decodeReattemptIndicatorTlv(reader, current, flatFields, node);
                case 0x72 -> current = decodeNestedServiceLevelAaContainerTlvE(reader, current, flatFields, node);
                case 0x77 -> current = decodeGenericTlvE(reader, current, "ATSSS container", node);
                default -> {
                    node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(current, end - current).toByteArray()), current, end - current));
                    return;
                }
            }
        }
    }

    private void decodeQosRules(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        int current = offset;
        int end = offset + length;
        int index = 1;
        while (current < end && reader.remaining(current) > 0) {
            int start = current;
            if (reader.remaining(current) < 3) {
                break;
            }
            int qosRuleId = reader.u8(current);
            current++;
            int ruleLength = reader.u16(current);
            DecodedFieldNode flowNode = new DecodedFieldNode("QoS rule " + index, "", start, 3 + ruleLength);
            node.addChild(flowNode);
            addField(flowNode, flatFields, "nas-5gs.sm.qos_rule_id", Integer.toString(qosRuleId), start, 1);
            flowNode.addChild(new DecodedFieldNode("nas-5gs.sm.length", Integer.toString(ruleLength), current, 2));
            current += 2;
            if (current + ruleLength > end || reader.remaining(current) < ruleLength) {
                break;
            }
            int descriptor = reader.u8(current);
            addField(flowNode, flatFields, "nas-5gs.sm.rop", Integer.toString((descriptor >>> 5) & 0x07), current, 1);
            addField(flowNode, flatFields, "nas-5gs.sm.dqr", Integer.toString((descriptor >>> 4) & 0x01), current, 1);
            addField(flowNode, flatFields, "nas-5gs.sm.nof_pkt_filters", Integer.toString(descriptor & 0x0f), current, 1);
            current++;
            int ruleEnd = current + (ruleLength - 1);
            if (current < ruleEnd) {
                flowNode.addChild(new DecodedFieldNode("Payload", hex(reader.slice(current, ruleEnd - current).toByteArray()), current, ruleEnd - current));
                current = ruleEnd;
            }
            index++;
        }
    }

    private void decodeSessionAmbr(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        addField(node, flatFields, "nas-5gs.sm.unit_for_session_ambr_dl", Integer.toString(reader.u8(offset)), offset, 1);
        addField(node, flatFields, "nas-5gs.sm.session_ambr_dl", Integer.toString(reader.u16(offset + 1)), offset + 1, 2);
        addField(node, flatFields, "nas-5gs.sm.unit_for_session_ambr_ul", Integer.toString(reader.u8(offset + 3)), offset + 3, 1);
        addField(node, flatFields, "nas-5gs.sm.session_ambr_ul", Integer.toString(reader.u16(offset + 4)), offset + 4, 2);
    }

    private int decodeDnnTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode dnnNode = new DecodedFieldNode("DNN", "", offset, 2 + length);
        node.addChild(dnnNode);
        dnnNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        if (length > 0) {
            addField(dnnNode, flatFields, "nas-5gs.cmn.dnn", decodeDnn(reader.slice(offset + 2, length).toByteArray()), offset + 2, length);
        }
        return offset + 2 + length;
    }

    private int decode5gsmNetworkFeatureSupportTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode featureNode = new DecodedFieldNode("5GSM network feature support", "", offset, 2 + length);
        node.addChild(featureNode);
        featureNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        int octet = reader.u8(offset + 2);
        addField(featureNode, flatFields, "nas-5gs.sm.naps", Integer.toString((octet >>> 1) & 0x01), offset + 2, 1);
        addField(featureNode, flatFields, "nas-5gs.sm.ept_s1", Integer.toString(octet & 0x01), offset + 2, 1);
        return offset + 2 + length;
    }

    private int decodeMappedEpsBearerContextsTlvE(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 2) < 2) {
            return offset;
        }
        int length = reader.u16(offset + 1);
        if (reader.remaining(offset + 3) < length) {
            return offset;
        }
        DecodedFieldNode mappedNode = new DecodedFieldNode("Mapped EPS bearer contexts", "", offset, 3 + length);
        node.addChild(mappedNode);
        mappedNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 2));

        int current = offset + 3;
        int end = current + length;
        int contextIndex = 1;
        while (current < end && reader.remaining(current) > 0) {
            if (end - current < 4 || reader.remaining(current + 3) < 1) {
                break;
            }
            int contextStart = current;
            int bearerIdentity = (reader.u8(current) >>> 4) & 0x0f;
            current++;
            int contextLength = reader.u16(current);
            current += 2;
            if (contextLength < 1 || current + contextLength > end || reader.remaining(current) < contextLength) {
                break;
            }

            DecodedFieldNode contextNode = new DecodedFieldNode(
                    "Mapped EPS bearer context " + contextIndex,
                    "",
                    contextStart,
                    3 + contextLength
            );
            mappedNode.addChild(contextNode);
            addField(contextNode, flatFields, "nas-5gs.sm.mapd_eps_b_cont_id", Integer.toString(bearerIdentity), contextStart, 1);
            contextNode.addChild(new DecodedFieldNode("Length", Integer.toString(contextLength), contextStart + 1, 2));

            int descriptor = reader.u8(current);
            addField(contextNode, flatFields, "nas-5gs.sm.mapd_eps_b_cont_opt_code", Integer.toString((descriptor >>> 6) & 0x03), current, 1);
            addField(contextNode, flatFields, "nas-5gs.sm.mapd_eps_b_cont_num_eps_parms", Integer.toString(descriptor & 0x0f), current, 1);
            if (((descriptor >>> 6) & 0x03) == 3) {
                addField(contextNode, flatFields, "nas-5gs.sm.mapd_eps_b_cont_E_mod", Integer.toString((descriptor >>> 4) & 0x01), current, 1);
            } else {
                addField(contextNode, flatFields, "nas-5gs.sm.mapd_eps_b_cont_E", Integer.toString((descriptor >>> 4) & 0x01), current, 1);
            }
            current++;

            int remainingParameters = descriptor & 0x0f;
            int parameterIndex = 1;
            int contextEnd = contextStart + 3 + contextLength;
            while (remainingParameters > 0 && current < contextEnd && reader.remaining(current) > 0) {
                if (contextEnd - current < 2 || reader.remaining(current + 1) < 1) {
                    break;
                }
                int parameterStart = current;
                int parameterId = reader.u8(current);
                current++;
                int parameterLength = reader.u8(current);
                current++;
                if (current + parameterLength > contextEnd || reader.remaining(current) < parameterLength) {
                    break;
                }

                DecodedFieldNode parameterNode = new DecodedFieldNode(
                        "EPS parameter " + parameterIndex,
                        "",
                        parameterStart,
                        2 + parameterLength
                );
                contextNode.addChild(parameterNode);
                addField(parameterNode, flatFields, "nas-5gs.sm.mapd_eps_b_cont_param_id", Integer.toString(parameterId), parameterStart, 1);
                parameterNode.addChild(new DecodedFieldNode("Length", Integer.toString(parameterLength), parameterStart + 1, 1));
                if (parameterLength > 0) {
                    addField(
                            parameterNode,
                            flatFields,
                            "nas-5gs.sm.mapd_eps_b_cont_eps_param_cont",
                            hex(reader.slice(current, parameterLength).toByteArray()),
                            current,
                            parameterLength
                    );
                }
                current += parameterLength;
                parameterIndex++;
                remainingParameters--;
            }

            if (current < contextEnd) {
                contextNode.addChild(new DecodedFieldNode("Payload", hex(reader.slice(current, contextEnd - current).toByteArray()), current, contextEnd - current));
                current = contextEnd;
            }
            contextIndex++;
        }

        if (current < end) {
            mappedNode.addChild(new DecodedFieldNode("Payload", hex(reader.slice(current, end - current).toByteArray()), current, end - current));
        }
        return offset + 3 + length;
    }

    private int decodeEcnMarkingForL4sIndicationTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode ecnNode = new DecodedFieldNode("ECN marking for L4S indication", "", offset, 2 + length);
        node.addChild(ecnNode);
        ecnNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        for (int i = 0; i < length; i++) {
            addField(
                    ecnNode,
                    flatFields,
                    "nas-5gs.sm.ecn_mark_l4s_ind.qri",
                    Integer.toString(reader.u8(offset + 2 + i)),
                    offset + 2 + i,
                    1
            );
        }
        return offset + 2 + length;
    }

    private int decode5gsmCongestionReattemptIndicatorTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode indicatorNode = new DecodedFieldNode("5GSM congestion re-attempt indicator", "", offset, 2 + length);
        node.addChild(indicatorNode);
        indicatorNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        int octet = reader.u8(offset + 2);
        addField(indicatorNode, flatFields, "nas-5gs.sm.catbo", Integer.toString((octet >>> 1) & 0x01), offset + 2, 1);
        addField(indicatorNode, flatFields, "nas-5gs.sm.abo", Integer.toString(octet & 0x01), offset + 2, 1);
        return offset + 2 + length;
    }

    private int decodeReattemptIndicatorTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode indicatorNode = new DecodedFieldNode("Re-attempt indicator", "", offset, 2 + length);
        node.addChild(indicatorNode);
        indicatorNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        int octet = reader.u8(offset + 2);
        addField(indicatorNode, flatFields, "nas-5gs.sm.eplmnc", Integer.toString((octet >>> 1) & 0x01), offset + 2, 1);
        addField(indicatorNode, flatFields, "nas-5gs.sm.ratc", Integer.toString(octet & 0x01), offset + 2, 1);
        return offset + 2 + length;
    }

    private int decodeSorPlmnAccessList(
            Nas5gsIeReader reader,
            int current,
            int end,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        int index = 1;
        while (current + 5 <= end && reader.remaining(current) >= 5) {
            DecodedFieldNode itemNode = new DecodedFieldNode("List item " + index, "", current, 5);
            node.addChild(itemNode);
            int b1 = reader.u8(current);
            int b2 = reader.u8(current + 1);
            int b3 = reader.u8(current + 2);
            addField(itemNode, flatFields, "e212.mcc", decodeMcc(b1, b2), current, 2);
            addField(itemNode, flatFields, "e212.mnc", decodeMnc(b2, b3), current + 1, 2);
            itemNode.addChild(new DecodedFieldNode("Access technology 1", Integer.toString(reader.u8(current + 3)), current + 3, 1));
            itemNode.addChild(new DecodedFieldNode("Access technology 2", Integer.toString(reader.u8(current + 4)), current + 4, 1));
            current += 5;
            index++;
        }
        return current;
    }

    private int decodeSorAdditionalParameters(
            Nas5gsIeReader reader,
            int current,
            int end,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (current >= end || reader.remaining(current) < 1) {
            return current;
        }
        int listLength = reader.u8(current);
        addField(node, flatFields, "nas-5gs.sor_plmn_id_act_len", Integer.toString(listLength), current, 1);
        current++;
        int listEnd = Math.min(end, current + listLength);
        current = decodeSorPlmnAccessList(reader, current, listEnd, flatFields, node);
        if (current >= end || reader.remaining(current) < 1) {
            return current;
        }
        int octetO = reader.u8(current);
        addField(node, flatFields, "nas-5gs.sor_sssli", Integer.toString((octetO >>> 3) & 0x01), current, 1);
        addField(node, flatFields, "nas-5gs.sor_sssi", Integer.toString((octetO >>> 2) & 0x01), current, 1);
        addField(node, flatFields, "nas-5gs.sor_sscmi", Integer.toString((octetO >>> 1) & 0x01), current, 1);
        addField(node, flatFields, "nas-5gs.sor_si", Integer.toString(octetO & 0x01), current, 1);
        current++;

        if (((octetO & 0x01) != 0) && current + 2 <= end && reader.remaining(current) >= 2) {
            int contLen = reader.u16(current);
            addField(node, flatFields, "nas-5gs.sor_cmci_len", Integer.toString(contLen), current, 2);
            current += 2;
            if (contLen > 0 && current + contLen <= end && reader.remaining(current) >= contLen) {
                addField(node, flatFields, "nas-5gs.sor_cmci_payload", hex(reader.slice(current, contLen).toByteArray()), current, contLen);
                current += contLen;
            }
        }
        if (((octetO & 0x04) != 0) && current + 2 <= end && reader.remaining(current) >= 2) {
            int contLen = reader.u16(current);
            addField(node, flatFields, "nas-5gs.sor_snpn_si_len", Integer.toString(contLen), current, 2);
            current += 2;
            if (contLen > 0 && current + contLen <= end && reader.remaining(current) >= contLen) {
                addField(node, flatFields, "nas-5gs.sor_snpn_si_payload", hex(reader.slice(current, contLen).toByteArray()), current, contLen);
                current += contLen;
            }
        }
        if (((octetO & 0x08) != 0) && current + 2 <= end && reader.remaining(current) >= 2) {
            int contLen = reader.u16(current);
            addField(node, flatFields, "nas-5gs.sor_snpn_si_ls_len", Integer.toString(contLen), current, 2);
            current += 2;
            if (contLen > 0 && current + contLen <= end && reader.remaining(current) >= contLen) {
                addField(node, flatFields, "nas-5gs.sor_snpn_si_ls_payload", hex(reader.slice(current, contLen).toByteArray()), current, contLen);
                current += contLen;
            }
        }
        return current;
    }

    private int decode5gsUpdateTypeTlv(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (length < 1 || reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode("5GS update type", "", offset, 2 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        int octet = reader.u8(offset + 2);
        addField(node, flatFields, "nas-5gs.mm.eps_pnb_ciot", Integer.toString((octet >>> 4) & 0x03), offset + 2, 1);
        addField(node, flatFields, "nas-5gs.mm.5gs_pnb_ciot", Integer.toString((octet >>> 2) & 0x03), offset + 2, 1);
        addField(node, flatFields, "nas-5gs.mm.ng_ran_rcu", Integer.toString((octet >>> 1) & 0x01), offset + 2, 1);
        addField(node, flatFields, "nas-5gs.mm.sms_requested", Integer.toString(octet & 0x01), offset + 2, 1);
        return offset + 2 + length;
    }

    private int decodeGenericTlv(
            Nas5gsIeReader reader,
            int offset,
            String nodeName,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 1) {
            return offset;
        }
        int length = reader.u8(offset + 1);
        if (reader.remaining(offset + 2) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode(nodeName, "", offset, 2 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 1));
        if (length > 0) {
            node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset + 2, length).toByteArray()), offset + 2, length));
        }
        return offset + 2 + length;
    }

    private int decodeGenericTlvE(
            Nas5gsIeReader reader,
            int offset,
            String nodeName,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset + 1) < 2) {
            return offset;
        }
        int length = reader.u16(offset + 1);
        if (reader.remaining(offset + 3) < length) {
            return offset;
        }
        DecodedFieldNode node = new DecodedFieldNode(nodeName, "", offset, 3 + length);
        messageNode.addChild(node);
        node.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 2));
        if (length > 0) {
            node.addChild(new DecodedFieldNode("Payload", hex(reader.slice(offset + 3, length).toByteArray()), offset + 3, length));
        }
        return offset + 3 + length;
    }

    private void decode5gmmCapability(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode node
    ) {
        if (length >= 1 && reader.remaining(offset) >= 1) {
            int octet = reader.u8(offset);
            addBitField(node, flatFields, "nas-5gs.mm.sgc_b7", (octet >>> 7) & 0x01, offset);
            addBitField(node, flatFields, "nas-5gs.mm.5g_iphc_cp_ciot_b6", (octet >>> 6) & 0x01, offset);
            addBitField(node, flatFields, "nas-5gs.mm.n3_data_b5", (octet >>> 5) & 0x01, offset);
            addBitField(node, flatFields, "nas-5gs.mm.5g_cp_ciot_b4", (octet >>> 4) & 0x01, offset);
            addBitField(node, flatFields, "nas-5gs.mm.restrict_ec_b3", (octet >>> 3) & 0x01, offset);
            addBitField(node, flatFields, "nas-5gs.mm.lpp_cap_b2", (octet >>> 2) & 0x01, offset);
            addBitField(node, flatFields, "nas-5gs.mm.ho_attach_b1", (octet >>> 1) & 0x01, offset);
            addBitField(node, flatFields, "nas-5gs.mm.s1_mode_b0", octet & 0x01, offset);
        }
        if (length >= 2 && reader.remaining(offset + 1) >= 1) {
            int octet = reader.u8(offset + 1);
            addBitField(node, flatFields, "nas-5gs.mm.racs_b7", (octet >>> 7) & 0x01, offset + 1);
            addBitField(node, flatFields, "nas-5gs.mm.nssaa_b6", (octet >>> 6) & 0x01, offset + 1);
            addBitField(node, flatFields, "nas-5gs.mm.5g_lcs_b5", (octet >>> 5) & 0x01, offset + 1);
            addBitField(node, flatFields, "nas-5gs.mm.v2xcnpc5_b4", (octet >>> 4) & 0x01, offset + 1);
            addBitField(node, flatFields, "nas-5gs.mm.v2xcepc5_b3", (octet >>> 3) & 0x01, offset + 1);
            addBitField(node, flatFields, "nas-5gs.mm.v2x_b2", (octet >>> 2) & 0x01, offset + 1);
            addBitField(node, flatFields, "nas-5gs.mm.5g_up_ciot_b1", (octet >>> 1) & 0x01, offset + 1);
            addBitField(node, flatFields, "nas-5gs.mm.5g_srvcc_b0", octet & 0x01, offset + 1);
        }
        if (length >= 3 && reader.remaining(offset + 2) >= 1) {
            int octet = reader.u8(offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.prose_l2relay_b7", (octet >>> 7) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.prose_dc_b6", (octet >>> 6) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.prose_dd_b5", (octet >>> 5) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.er_nssai_b4", (octet >>> 4) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.ehc_cp_ciot_b3", (octet >>> 3) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.multiple_up_b2", (octet >>> 2) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.wsusa_b1", (octet >>> 1) & 0x01, offset + 2);
            addBitField(node, flatFields, "nas-5gs.mm.cag_b0", octet & 0x01, offset + 2);
        }
        if (length >= 4 && reader.remaining(offset + 3) >= 1) {
            int octet = reader.u8(offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.pr_b7", (octet >>> 7) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.rpr_b6", (octet >>> 6) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.piv_b5", (octet >>> 5) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.ncr_b4", (octet >>> 4) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.nr_pssi_b3", (octet >>> 3) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.5g_prose_l3rmt_b2", (octet >>> 2) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.5g_prose_l2rmt_b1", (octet >>> 1) & 0x01, offset + 3);
            addBitField(node, flatFields, "nas-5gs.mm.5g_prose_l3relay_b0", octet & 0x01, offset + 3);
        }
        if (length >= 5 && reader.remaining(offset + 4) >= 1) {
            int octet = reader.u8(offset + 4);
            addBitField(node, flatFields, "nas-5gs.mm.mpsiu_b7", (octet >>> 7) & 0x01, offset + 4);
            addBitField(node, flatFields, "nas-5gs.mm.uas_b6", (octet >>> 6) & 0x01, offset + 4);
            addBitField(node, flatFields, "nas-5gs.mm.nsag_b5", (octet >>> 5) & 0x01, offset + 4);
            addBitField(node, flatFields, "nas-5gs.mm.ex_cag_b4", (octet >>> 4) & 0x01, offset + 4);
            addBitField(node, flatFields, "nas-5gs.mm.ssnpnsi_b3", (octet >>> 3) & 0x01, offset + 4);
            addBitField(node, flatFields, "nas-5gs.mm.event_notif_b2", (octet >>> 2) & 0x01, offset + 4);
            addBitField(node, flatFields, "nas-5gs.mm.mint_b1", (octet >>> 1) & 0x01, offset + 4);
            addBitField(node, flatFields, "nas-5gs.mm.nssrg_b0", octet & 0x01, offset + 4);
        }
        if (length >= 6 && reader.remaining(offset + 5) >= 1) {
            int octet = reader.u8(offset + 5);
            addBitField(node, flatFields, "nas-5gs.mm.sbts_b7", (octet >>> 7) & 0x01, offset + 5);
            addBitField(node, flatFields, "nas-5gs.mm.nsr_b6", (octet >>> 6) & 0x01, offset + 5);
            addBitField(node, flatFields, "nas-5gs.mm.ladn_ds_b5", (octet >>> 5) & 0x01, offset + 5);
            addBitField(node, flatFields, "nas-5gs.mm.rantiming_b4", (octet >>> 4) & 0x01, offset + 5);
            addBitField(node, flatFields, "nas-5gs.mm.eci_b3", (octet >>> 3) & 0x01, offset + 5);
            addBitField(node, flatFields, "nas-5gs.mm.esi_b2", (octet >>> 2) & 0x01, offset + 5);
            addBitField(node, flatFields, "nas-5gs.mm.rcman_b1", (octet >>> 1) & 0x01, offset + 5);
            addBitField(node, flatFields, "nas-5gs.mm.rcmap_b0", octet & 0x01, offset + 5);
        }
    }

    private void addField(
            DecodedFieldNode messageNode,
            Map<String, String> flatFields,
            String name,
            String value,
            int offset,
            int length
    ) {
        flatFields.putIfAbsent(name, value);
        messageNode.addChild(new DecodedFieldNode(name, value, offset, length));
    }

    private void addBitField(
            DecodedFieldNode messageNode,
            Map<String, String> flatFields,
            String name,
            int value,
            int offset
    ) {
        addField(messageNode, flatFields, name, Integer.toString(value), offset, 1);
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private String decodeDnn(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length);
        int index = 0;
        while (index < bytes.length) {
            int labelLength = bytes[index] & 0xff;
            index++;
            if (labelLength == 0 || index + labelLength > bytes.length) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append('.');
            }
            for (int i = 0; i < labelLength; i++) {
                sb.append((char) (bytes[index + i] & 0xff));
            }
            index += labelLength;
        }
        return sb.toString();
    }

    private String decodeUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String decodeIpv4(byte[] bytes) {
        if (bytes == null || bytes.length != 4) {
            return "";
        }
        return (bytes[0] & 0xff) + "."
                + (bytes[1] & 0xff) + "."
                + (bytes[2] & 0xff) + "."
                + (bytes[3] & 0xff);
    }

    private String decodeIpv6(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            return "";
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception ignored) {
            return hex(bytes);
        }
    }

    private String decodeInterfaceIdIpv6(byte[] bytes) {
        if (bytes == null || bytes.length != 8) {
            return "";
        }
        return String.format(
                Locale.ROOT,
                "::%x:%x:%x:%x",
                ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff),
                ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff),
                ((bytes[4] & 0xff) << 8) | (bytes[5] & 0xff),
                ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff)
        );
    }

    private String decodeMac(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 3 - 1);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xff));
        }
        return sb.toString();
    }

    private String decodeMcc(int b1, int b2) {
        return new StringBuilder(3)
                .append(b1 & 0x0f)
                .append((b1 >>> 4) & 0x0f)
                .append(b2 & 0x0f)
                .toString();
    }

    private String decodeMnc(int b2, int b3) {
        int digit3 = (b2 >>> 4) & 0x0f;
        StringBuilder sb = new StringBuilder(3);
        sb.append(b3 & 0x0f);
        sb.append((b3 >>> 4) & 0x0f);
        if (digit3 != 0x0f) {
            sb.append(digit3);
        }
        return sb.toString();
    }

    private void decodeBitOctet(
            DecodedFieldNode node,
            Map<String, String> flatFields,
            int offset,
            int octet,
            String[] fieldNames
    ) {
        for (int i = 0; i < fieldNames.length; i++) {
            String fieldName = fieldNames[i];
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            int bit = (octet >>> (7 - i)) & 0x01;
            addField(node, flatFields, fieldName, Integer.toString(bit), offset, 1);
        }
    }

    private void decodePsiOctet(
            DecodedFieldNode node,
            Map<String, String> flatFields,
            String fieldPrefix,
            int octet,
            int psiBase,
            int offset
    ) {
        for (int bit = 0; bit < 8; bit++) {
            int psi = psiBase + bit;
            int value = (octet >>> bit) & 0x01;
            String suffix = psi + "_b" + bit;
            addField(node, flatFields, fieldPrefix + suffix, Integer.toString(value), offset, 1);
        }
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String payloadContainerTypeName(int payloadType) {
        return switch (payloadType) {
            case 0x01 -> "N1 SM information";
            case 0x02 -> "SMS";
            case 0x03 -> "LTE Positioning Protocol (LPP) message container";
            case 0x04 -> "SOR transparent container";
            case 0x05 -> "UE policy container";
            case 0x06 -> "UE parameters update transparent container";
            case 0x07 -> "Location services message container";
            case 0x08 -> "CIoT user data container";
            case 0x09 -> "Service-level-AA container";
            case 0x0a -> "Event notification";
            case 0x0b -> "UPP-CMI container";
            case 0x0c -> "SLPP message container";
            case 0x0f -> "Multiple payloads";
            default -> "Unknown";
        };
    }

    private String nas5gsSmMessageTypeName(int messageType) {
        return switch (messageType) {
            case 0xc1 -> "PDU session establishment request";
            case 0xc2 -> "PDU session establishment accept";
            case 0xc3 -> "PDU session establishment reject";
            case 0xc5 -> "PDU session authentication command";
            case 0xc6 -> "PDU session authentication complete";
            case 0xc7 -> "PDU session authentication result";
            case 0xc9 -> "PDU session modification request";
            case 0xca -> "PDU session modification reject";
            case 0xcb -> "PDU session modification command";
            case 0xcc -> "PDU session modification complete";
            case 0xcd -> "PDU session modification command reject";
            case 0xd1 -> "PDU session release request";
            case 0xd2 -> "PDU session release reject";
            case 0xd3 -> "PDU session release command";
            case 0xd4 -> "PDU session release complete";
            case 0xd6 -> "5GSM status";
            case 0xd8 -> "Service-level authentication command";
            case 0xd9 -> "Service-level authentication complete";
            case 0xda -> "Remote UE report";
            case 0xdb -> "Remote UE report response";
            default -> null;
        };
    }
}
