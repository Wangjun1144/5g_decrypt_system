package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsStructuredDecodeResult;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsStructuredDissector;
import com.example.procedure.infrastructure.dissection.nas.message.AuthenticationRequestNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.AuthenticationResponseNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.IdentityResponseNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.IdentityRequestNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.RegistrationRequestNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.SecurityModeCommandNasMessageDissector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Nas5gsStructuredDissectorTests {

    @Test
    void should_build_structured_tree_for_registration_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{0x7e, 0x00, 0x41, 0x01})
        );

        assertEquals(0x41, result.getMessageType());
        assertEquals("Registration request", result.getMessageTypeName());
        assertEquals("126", result.getDecodedFields().get("nas-5gs.epd"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.security_header_type"));
        assertEquals("65", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5gs_reg_type"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.tsc.h1"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.nas_key_set_id.h1"));

        assertFalse(result.getFieldTree().isEmpty());
        DecodedFieldNode root = result.getFieldTree().get(0);
        assertEquals("nas-5gs", root.getName());
        assertFalse(root.getChildren().isEmpty());

        DecodedFieldNode messageNode = root.getChildren().stream()
                .filter(node -> "Registration request".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(messageNode);
        DecodedFieldNode regTypeNode = messageNode.getChildren().stream()
                .filter(node -> "nas-5gs.mm.5gs_reg_type".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(regTypeNode);
        assertEquals("1", regTypeNode.getValue());
    }

    @Test
    void should_build_structured_fields_for_registration_request_with_mobile_identity() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40,
                        (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        0x2e, 0x04, (byte) 0xf0, 0x70, 0x00, 0x00
                })
        );

        assertEquals("11", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.spare_b7"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.spare_b6"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.spare_b5"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.spare_b4"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.spare_b3"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.mm.type_id"));
        assertEquals("001", result.getDecodedFields().get("e212.guami.mcc"));
        assertEquals("01", result.getDecodedFields().get("e212.guami.mnc"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.amf_region_id"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.amf_set_id"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.amf_pointer"));
        assertEquals("c00007ec", result.getDecodedFields().get("nas-5gs.5g_tmsi"));
        assertEquals("3221227500", result.getDecodedFields().get("3gpp.tmsi"));
        assertEquals("0x2e", result.getDecodedFields().get("nas-5gs.mm.elem_id"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5g_ea0"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.128_5g_ea1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.128_5g_ea2"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.128_5g_ea3"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ea4"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ea5"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ea6"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ea7"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.ia0"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5g_128_ia1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5g_128_ia2"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5g_128_ia3"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_128_ia4"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ia5"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ia6"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ia7"));
    }

    @Test
    void should_build_structured_fields_for_registration_request_with_5g_s_tmsi_identity() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x07, 0x04,
                        0x00, 0x40,
                        (byte) 0xc0, 0x00, 0x07, (byte) 0xec
                })
        );

        assertEquals("7", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("4", result.getDecodedFields().get("nas-5gs.mm.type_id"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.odd_even"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.amf_set_id"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.amf_pointer"));
        assertEquals("c00007ec", result.getDecodedFields().get("nas-5gs.5g_tmsi"));
        assertEquals("3221227500", result.getDecodedFields().get("3gpp.tmsi"));
    }

    @Test
    void should_build_structured_fields_for_registration_request_with_imei_identity() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x08, 0x1b,
                        0x32, 0x54, 0x76, (byte) 0x98, 0x10, 0x32, 0x54, (byte) 0xf5
                })
        );

        assertEquals("8", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("3", result.getDecodedFields().get("nas-5gs.mm.type_id"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.odd_even"));
        assertEquals("123456789012345", result.getDecodedFields().get("nas-5gs.mm.imei"));
    }

    @Test
    void should_build_structured_fields_for_registration_request_with_imeisv_identity() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x09, 0x1d,
                        0x32, 0x54, 0x76, (byte) 0x98, 0x10, 0x32, 0x54, 0x76, (byte) 0xf7
                })
        );

        assertEquals("9", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("5", result.getDecodedFields().get("nas-5gs.mm.type_id"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.odd_even"));
        assertEquals("12345678901234567", result.getDecodedFields().get("nas-5gs.mm.imeisv"));
    }

    @Test
    void should_build_structured_fields_for_registration_request_with_mac_address_identity() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x07, 0x0e,
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66
                })
        );

        assertEquals("7", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("6", result.getDecodedFields().get("nas-5gs.mm.type_id"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.mauri"));
        assertEquals("11:22:33:44:55:66", result.getDecodedFields().get("nas-5gs.mm.mac_addr"));
    }

    @Test
    void should_build_structured_fields_for_registration_request_with_eui64_identity() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x09, 0x0f,
                        0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef
                })
        );

        assertEquals("9", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("7", result.getDecodedFields().get("nas-5gs.mm.type_id"));
        assertEquals("01:23:45:67:89:ab:cd:ef", result.getDecodedFields().get("nas-5gs.mm.eui_64"));
    }

    @Test
    void should_build_structured_fields_for_registration_request_optional_ies_in_source_order() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40,
                        (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0xb1,
                        (byte) 0x83,
                        0x77, 0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40,
                        (byte) 0xc0, 0x00, 0x07, (byte) 0xec
                })
        );

        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.sprti_b1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.raai_b0"));
        assertEquals("3", result.getDecodedFields().get("nas-5gs.mm.pld_cont_type"));

        DecodedFieldNode root = result.getFieldTree().get(0);
        DecodedFieldNode messageNode = root.getChildren().stream()
                .filter(node -> "Registration request".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(messageNode);
        DecodedFieldNode additionalGutiNode = messageNode.getChildren().stream()
                .filter(node -> "Additional GUTI".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(additionalGutiNode);
        DecodedFieldNode additionalGutiIdentityNode = additionalGutiNode.getChildren().stream()
                .filter(node -> "5GS mobile identity".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(additionalGutiIdentityNode);
    }

    @Test
    void should_build_structured_fields_for_registration_request_with_5gmm_capability() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40,
                        (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        0x10, 0x06,
                        (byte) 0xa5, 0x5a, (byte) 0xc3, 0x3c, (byte) 0xf0, 0x0f
                })
        );

        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.sgc_b7"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_iphc_cp_ciot_b6"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.n3_data_b5"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_cp_ciot_b4"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.restrict_ec_b3"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.lpp_cap_b2"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.ho_attach_b1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.s1_mode_b0"));

        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.racs_b7"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.nssaa_b6"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_lcs_b5"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.v2xcnpc5_b4"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.v2xcepc5_b3"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.v2x_b2"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5g_up_ciot_b1"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_srvcc_b0"));

        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.prose_l2relay_b7"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.prose_dc_b6"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.prose_dd_b5"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.er_nssai_b4"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.ehc_cp_ciot_b3"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.multiple_up_b2"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.wsusa_b1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.cag_b0"));

        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.pr_b7"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.rpr_b6"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.piv_b5"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.ncr_b4"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.nr_pssi_b3"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5g_prose_l3rmt_b2"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_prose_l2rmt_b1"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_prose_l3relay_b0"));

        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.mpsiu_b7"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.uas_b6"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.nsag_b5"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.ex_cag_b4"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.ssnpnsi_b3"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.event_notif_b2"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.mint_b1"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.nssrg_b0"));

        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.sbts_b7"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.nsr_b6"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.ladn_ds_b5"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.rantiming_b4"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.eci_b3"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.esi_b2"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.rcman_b1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.rcmap_b0"));
    }

    @Test
    void should_follow_wireshark_optional_ie_order_for_registration_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40,
                        (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0xc2,
                        0x10, 0x01, (byte) 0x80,
                        0x2e, 0x04, (byte) 0xf0, 0x70, 0x00, 0x00,
                        0x2f, 0x02, 0x01, 0x01,
                        0x52, 0x00, (byte) 0xf1, 0x10, 0x00, 0x00, 0x01,
                        0x17, 0x02, 0x33, 0x44,
                        0x40, 0x02, 0x55, 0x66,
                        0x50, 0x02, 0x77, (byte) 0x88,
                        (byte) 0xb1,
                        0x2b, 0x01, 0x03,
                        0x77, 0x00, 0x07, 0x04, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        0x25, 0x02, 0x12, 0x34,
                        0x18, 0x01, 0x01,
                        0x51, 0x01, 0x02,
                        0x70, 0x00, 0x02, (byte) 0xde, (byte) 0xad,
                        0x74, 0x00, 0x0a, 0x09, 0x08, 0x69, 0x6e, 0x74, 0x65, 0x72, 0x6e, 0x65, 0x74,
                        (byte) 0x83,
                        0x7b, 0x00, 0x02, (byte) 0xbe, (byte) 0xef,
                        (byte) 0x91,
                        0x53, 0x01, 0x07
                })
        );

        assertEquals("2", result.getDecodedFields().get("nas-5gs.mm.nas_key_set_id"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.sgc_b7"));
        assertEquals("3", result.getDecodedFields().get("nas-5gs.mm.pld_cont_type"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.network_slicing_indication"));

        DecodedFieldNode root = result.getFieldTree().get(0);
        DecodedFieldNode messageNode = root.getChildren().stream()
                .filter(node -> "Registration request".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(messageNode);

        assertNodeOrder(messageNode,
                "Non-current native NAS key set identifier",
                "5GMM capability",
                "UE security capability",
                "Requested NSSAI",
                "Last visited registered TAI",
                "S1 UE network capability",
                "Uplink data status",
                "PDU session status",
                "MICO indication",
                "UE status",
                "Additional GUTI",
                "Allowed PDU session status",
                "UE's usage setting",
                "Requested DRX parameters",
                "EPS NAS message container",
                "LADN indication",
                "Payload container type",
                "Payload container",
                "Network slicing indication",
                "5GS update type"
        );

        DecodedFieldNode requestedNssaiNode = messageNode.getChildren().stream()
                .filter(node -> "Requested NSSAI".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(requestedNssaiNode);
        assertEquals("2", requestedNssaiNode.getChildren().get(0).getValue());
        DecodedFieldNode requestedSnssaiNode = requestedNssaiNode.getChildren().stream()
                .filter(node -> "S-NSSAI 1".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(requestedSnssaiNode);
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.sst"));

        DecodedFieldNode epsNasContainerNode = messageNode.getChildren().stream()
                .filter(node -> "EPS NAS message container".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(epsNasContainerNode);
        assertEquals("2", epsNasContainerNode.getChildren().get(0).getValue());
        assertEquals("dead", epsNasContainerNode.getChildren().get(1).getValue());

        assertEquals("001", result.getDecodedFields().get("e212.mcc"));
        assertEquals("01", result.getDecodedFields().get("e212.mnc"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.tac"));
        assertEquals("internet", result.getDecodedFields().get("nas-5gs.cmn.dnn"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.ue_usage_setting"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.mm.drx_value"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.ng_ran_rcu"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.sms_requested"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.n1_mode_reg_b1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.s1_mode_reg_b0"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.ul_data_sts_psi_0_b0"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.ul_data_sts_psi_1_b1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.ul_data_sts_psi_14_b6"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.ul_data_sts_psi_15_b7"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.pdu_ses_sts_psi_0_b0"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.pdu_ses_sts_psi_1_b1"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.pdu_ses_sts_psi_8_b0"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.allow_pdu_ses_sts_psi_1_b1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.allow_pdu_ses_sts_psi_4_b4"));
        assertEquals("1", result.getDecodedFields().get("nas-eps.emm.128eea2"));
        assertEquals("1", result.getDecodedFields().get("nas-eps.emm.128eia1"));
    }

    @Test
    void should_decode_eps_nas_message_container_for_registration_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        0x70, 0x00, 0x05, 0x2d, 0x03, 0x11, 0x22, 0x33
                })
        );

        assertEquals("45", result.getDecodedFields().get("nas-eps.emm.elem_id"));
        assertEquals("112233", result.getDecodedFields().get("nas-eps.emm.res"));

        DecodedFieldNode root = result.getFieldTree().get(0);
        DecodedFieldNode messageNode = root.getChildren().stream()
                .filter(node -> "Registration request".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(messageNode);
        DecodedFieldNode epsNasContainerNode = messageNode.getChildren().stream()
                .filter(node -> "EPS NAS message container".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(epsNasContainerNode);
        assertEquals("5", epsNasContainerNode.getChildren().get(0).getValue());
        assertEquals("45", epsNasContainerNode.getChildren().get(1).getValue());
        assertEquals("3", epsNasContainerNode.getChildren().get(2).getValue());
        assertEquals("112233", epsNasContainerNode.getChildren().get(3).getValue());
    }

    @Test
    void should_decode_multiple_payload_container_for_registration_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x8f,
                        0x7b, 0x00, 0x18,
                        0x01,
                        0x00, 0x15,
                        0x21,
                        0x12, 0x01, 0x05,
                        0x25, 0x09, 0x03, 0x61, 0x70, 0x6e, 0x04, 0x74, 0x65, 0x73, 0x74,
                        0x2e, 0x05, 0x07, (byte) 0xd6, (byte) 0xaa, 0x55
                })
        );

        assertEquals("15", result.getDecodedFields().get("nas-5gs.mm.pld_cont_type"));
        assertEquals("5", result.getDecodedFields().get("nas-5gs.pdu_session_id"));
        assertEquals("apn.test", result.getDecodedFields().get("nas-5gs.cmn.dnn"));

        DecodedFieldNode root = result.getFieldTree().get(0);
        DecodedFieldNode messageNode = root.getChildren().stream()
                .filter(node -> "Registration request".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(messageNode);
        DecodedFieldNode payloadContainerNode = messageNode.getChildren().stream()
                .filter(node -> "Payload container".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(payloadContainerNode);
        assertEquals("24", payloadContainerNode.getChildren().get(0).getValue());
        assertEquals("1", payloadContainerNode.getChildren().get(1).getValue());

        DecodedFieldNode entryNode = payloadContainerNode.getChildren().stream()
                .filter(node -> "Payload container entry 1".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(entryNode);
        DecodedFieldNode nestedMessageTypeNode = entryNode.getChildren().stream()
                .filter(node -> "nas-5gs.sm.message_type_name".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(nestedMessageTypeNode);
        assertEquals("5GSM status", nestedMessageTypeNode.getValue());
        assertEquals("7", result.getDecodedFields().get("nas-5gs.proc_trans_id"));
        assertEquals("214", result.getDecodedFields().get("nas-5gs.sm.message_type"));
        DecodedFieldNode payloadNode = entryNode.getChildren().stream()
                .filter(node -> "Payload".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(payloadNode);
        assertEquals("55", payloadNode.getValue());
    }

    @Test
    void should_decode_service_level_aa_payload_container_for_registration_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x89,
                        0x7b, 0x00, 0x17,
                        0x10, 0x03, 0x61, 0x62, 0x63,
                        0x20, 0x05, 0x01, 0x01, 0x02, 0x03, 0x04,
                        0x30, 0x01, 0x09,
                        0x40, 0x01, 0x02,
                        0x70, 0x00, 0x02, (byte) 0xde, (byte) 0xad
                })
        );

        assertEquals("9", result.getDecodedFields().get("nas-5gs.mm.pld_cont_type"));
        assertEquals("abc", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.device_id"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.addr.type"));
        assertEquals("1.2.3.4", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.addr.ipv4"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.response.c2ar"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.response.slar"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.payload_type"));
        assertEquals("dead", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.payload"));
    }

    @Test
    void should_decode_sor_payload_container_for_registration_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x84,
                        0x7b, 0x00, 0x11,
                        0x0f,
                        0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                        (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff
                })
        );

        assertEquals("4", result.getDecodedFields().get("nas-5gs.mm.pld_cont_type"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor.msssnpnsils"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor.mssnpnsi"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor.mssi"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor.sor_data_type"));
        assertEquals("00112233445566778899aabbccddeeff", result.getDecodedFields().get("nas-5gs.mm.sor_mac_iue"));
    }

    @Test
    void should_decode_n1_sm_information_payload_container_for_registration_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x81,
                        0x7b, 0x00, 0x06,
                        0x2e, 0x05, 0x07, (byte) 0xd6, 0x2a, 0x00
                })
        );

        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.pld_cont_type"));
        assertEquals("5", result.getDecodedFields().get("nas-5gs.pdu_session_id"));
        assertEquals("7", result.getDecodedFields().get("nas-5gs.proc_trans_id"));
        assertEquals("214", result.getDecodedFields().get("nas-5gs.sm.message_type"));
        assertEquals("42", result.getDecodedFields().get("nas-5gs.sm.5gsm_cause"));

        DecodedFieldNode root = result.getFieldTree().get(0);
        DecodedFieldNode messageNode = root.getChildren().stream()
                .filter(node -> "Registration request".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(messageNode);
        DecodedFieldNode payloadContainerNode = messageNode.getChildren().stream()
                .filter(node -> "Payload container".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(payloadContainerNode);
        DecodedFieldNode typeNameNode = payloadContainerNode.getChildren().stream()
                .filter(node -> "nas-5gs.sm.message_type_name".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(typeNameNode);
        assertEquals("5GSM status", typeNameNode.getValue());
    }

    @Test
    void should_decode_pdu_session_establishment_request_inside_n1_sm_information() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x81,
                        0x7b, 0x00, 0x11,
                        0x2e, 0x05, 0x07, (byte) 0xc1,
                        0x09, 0x08,
                        (byte) 0x93,
                        (byte) 0xa2,
                        0x28, 0x03, (byte) 0x8d, (byte) 0xe3, 0x03,
                        0x55, 0x01, 0x40,
                        (byte) 0xb1
                })
        );

        assertEquals("193", result.getDecodedFields().get("nas-5gs.sm.message_type"));
        assertEquals("9", result.getDecodedFields().get("nas-5gs.sm.int_prot_max_data_rate_ul"));
        assertEquals("8", result.getDecodedFields().get("nas-5gs.sm.int_prot_max_data_rate_dl"));
        assertEquals("3", result.getDecodedFields().get("nas-5gs.sm.pdu_session_type"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.sm.sc_mode"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.tpmic"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.rqos"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.mpquic_ip"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.apmqf"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.mpquic_e"));
        assertEquals("5", result.getDecodedFields().get("nas-5gs.sm.max_nb_sup_pkt_flt.nb"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.apsr"));
    }

    @Test
    void should_decode_more_optional_ies_for_pdu_session_establishment_request_inside_n1_sm_information() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x81,
                        0x7b, 0x00, 0x36,
                        0x2e, 0x05, 0x07, (byte) 0xc1,
                        0x09, 0x08,
                        0x39, 0x03, 0x61, 0x62, 0x63,
                        0x66, 0x04, 0x7f, 0x01, 0x23, 0x11,
                        0x6e, 0x06, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55,
                        0x6f, 0x02, 0x12, 0x34,
                        0x74, 0x00, 0x02, (byte) 0xde, (byte) 0xad,
                        0x1f, 0x01, 0x02,
                        0x29, 0x05, 0x01, 0x0a, 0x14, 0x1e, 0x28,
                        0x34, 0x01, 0x09,
                        0x35, 0x01, 0x02,
                        0x36, 0x02, 0x55, 0x66
                })
        );

        assertEquals("abc", result.getDecodedFields().get("nas-5gs.sm.dm_spec_id"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.ip_hdr_comp_config.p0104"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.ip_hdr_comp_config.p0002"));
        assertEquals("291", result.getDecodedFields().get("nas-5gs.sm.ip_hdr_comp_config.max_cid"));
        assertEquals("17", result.getDecodedFields().get("nas-5gs.sm.ip_hdr_comp_config.add_hdr_compr_cxt_setup_params_type"));
        assertEquals("00:11:22:33:44:55", result.getDecodedFields().get("nas-5gs.sm.ds_tt_eth_port_mac_addr"));
        assertEquals("1234", result.getDecodedFields().get("nas-5gs.sm.ue_ds_tt_residence_time"));
        assertEquals("dead", result.getDecodedFields().get("nas-5gs.sm.port_mgmt_info_cont"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.sm.eth_hdr_comp_config.cid_len"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.pdu_ses_type"));
        assertEquals("10.20.30.40", result.getDecodedFields().get("nas-5gs.sm.pdu_addr_inf_ipv4"));
        assertEquals("9", result.getDecodedFields().get("nas-5gs.sm.pdu_session_pair_id"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.sm.rsn"));
    }

    @Test
    void should_decode_nested_service_level_aa_container_inside_pdu_session_establishment_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x81,
                        0x7b, 0x00, 0x11,
                        0x2e, 0x05, 0x07, (byte) 0xc1,
                        0x09, 0x08,
                        0x72, 0x00, 0x08,
                        0x10, 0x03, 0x78, 0x79, 0x7a,
                        0x40, 0x01, 0x04
                })
        );

        assertEquals("xyz", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.device_id"));
        assertEquals("4", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.payload_type"));
    }

    @Test
    void should_decode_pdu_session_establishment_accept_inside_n1_sm_information() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x81,
                        0x7b, 0x00, 0x29,
                        0x2e, 0x05, 0x07, (byte) 0xc2,
                        0x23,
                        0x00, 0x04, 0x01, 0x00, 0x01, (byte) 0xaa,
                        0x06, 0x01, 0x00, 0x64, 0x02, 0x00, (byte) 0xc8,
                        0x59, 0x2a,
                        0x25, 0x04, 0x03, 0x61, 0x70, 0x6e,
                        0x17, 0x01, 0x03,
                        (byte) 0x81,
                        0x72, 0x00, 0x08, 0x10, 0x03, 0x78, 0x79, 0x7a, 0x40, 0x01, 0x04
                })
        );

        assertEquals("194", result.getDecodedFields().get("nas-5gs.sm.message_type"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.sm.sel_sc_mode"));
        assertEquals("3", result.getDecodedFields().get("nas-5gs.sm.pdu_session_type"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.qos_rule_id"));
        assertEquals("5", result.getDecodedFields().get("nas-5gs.sm.rop"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.sm.dqr"));
        assertEquals("10", result.getDecodedFields().get("nas-5gs.sm.nof_pkt_filters"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.unit_for_session_ambr_dl"));
        assertEquals("100", result.getDecodedFields().get("nas-5gs.sm.session_ambr_dl"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.sm.unit_for_session_ambr_ul"));
        assertEquals("200", result.getDecodedFields().get("nas-5gs.sm.session_ambr_ul"));
        assertEquals("42", result.getDecodedFields().get("nas-5gs.sm.5gsm_cause"));
        assertEquals("apn", result.getDecodedFields().get("nas-5gs.cmn.dnn"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.naps"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.ept_s1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.apsi"));
        assertEquals("xyz", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.device_id"));
    }

    @Test
    void should_decode_pdu_session_establishment_reject_inside_n1_sm_information() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x81,
                        0x7b, 0x00, 0x1a,
                        0x2e, 0x05, 0x07, (byte) 0xc3,
                        0x2a,
                        0x37, 0x01, 0x22,
                        (byte) 0xf7,
                        0x61, 0x01, 0x03,
                        0x1d, 0x01, 0x03,
                        0x72, 0x00, 0x08, 0x10, 0x03, 0x78, 0x79, 0x7a, 0x40, 0x01, 0x04
                })
        );

        assertEquals("195", result.getDecodedFields().get("nas-5gs.sm.message_type"));
        assertEquals("42", result.getDecodedFields().get("nas-5gs.sm.5gsm_cause"));
        assertEquals("34", result.getDecodedFields().get("gprs_timer_3.timer_value"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.all_ssc_mode_b2"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.all_ssc_mode_b1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.all_ssc_mode_b0"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.catbo"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.abo"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.eplmnc"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.ratc"));
        assertEquals("xyz", result.getDecodedFields().get("nas-5gs.cmn.service_level_aa_param.device_id"));
    }

    @Test
    void should_decode_mapped_eps_bearer_contexts_and_ecn_inside_pdu_session_establishment_accept() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x81,
                        0x7b, 0x00, 0x20,
                        0x2e, 0x05, 0x07, (byte) 0xc2,
                        0x23,
                        0x00, 0x04, 0x01, 0x00, 0x01, (byte) 0xaa,
                        0x06, 0x01, 0x00, 0x64, 0x02, 0x00, (byte) 0xc8,
                        0x75, 0x00, 0x07, 0x50, 0x00, 0x04, 0x11, 0x01, 0x01, (byte) 0xab,
                        0x38, 0x02, 0x11, 0x22
                })
        );

        assertEquals("5", result.getDecodedFields().get("nas-5gs.sm.mapd_eps_b_cont_id"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.sm.mapd_eps_b_cont_opt_code"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.mapd_eps_b_cont_E"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.mapd_eps_b_cont_num_eps_parms"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sm.mapd_eps_b_cont_param_id"));
        assertEquals("ab", result.getDecodedFields().get("nas-5gs.sm.mapd_eps_b_cont_eps_param_cont"));
        assertEquals("17", result.getDecodedFields().get("nas-5gs.sm.ecn_mark_l4s_ind.qri"));
    }

    @Test
    void should_decode_sor_additional_parameter_payload_container_for_registration_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x84,
                        0x7b, 0x00, 0x21,
                        0x1c,
                        0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                        (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff,
                        0x00, 0x09,
                        0x05,
                        0x00, (byte) 0xf1, 0x10, 0x01, 0x02,
                        0x0f,
                        0x00, 0x02, 0x12, 0x34,
                        0x00, 0x01, 0x56
                })
        );

        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor_hdr0.ap"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor_hdr0.list_type"));
        assertEquals("5", result.getDecodedFields().get("nas-5gs.sor_plmn_id_act_len"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor_sssli"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor_sssi"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor_sscmi"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor_si"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.sor_cmci_len"));
        assertEquals("1234", result.getDecodedFields().get("nas-5gs.sor_cmci_payload"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.sor_snpn_si_len"));
        assertEquals("56", result.getDecodedFields().get("nas-5gs.sor_snpn_si_payload"));
    }

    @Test
    void should_decode_multiple_payload_container_optional_ies_for_registration_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new RegistrationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x41, 0x01,
                        0x00, 0x0b, (byte) 0xf2,
                        0x00, (byte) 0xf1, 0x10,
                        0x02, 0x00, 0x40, (byte) 0xc0, 0x00, 0x07, (byte) 0xec,
                        (byte) 0x8f,
                        0x7b, 0x00, 0x18,
                        0x01,
                        0x00, 0x15,
                        (byte) 0x51,
                        0x37, 0x01, 0x22,
                        0x58, 0x01, 0x19,
                        (byte) 0x80, 0x01, 0x05,
                        (byte) 0xa0, 0x01, 0x7f,
                        (byte) 0xf0, 0x01, 0x03,
                        0x2e, 0x01, 0x01, (byte) 0xd6, 0x24
                })
        );

        assertEquals("34", result.getDecodedFields().get("gprs_timer_3.timer_value"));
        assertEquals("25", result.getDecodedFields().get("nas-5gs.mm.5gmm_cause"));
        assertEquals("5", result.getDecodedFields().get("nas-5gs.mm.req_type"));
        assertEquals("127", result.getDecodedFields().get("nas-5gs.mm.ma_pdu_session_info_value"));
        assertEquals("3", result.getDecodedFields().get("nas-eps.esm.rel_assist_ind.ddx"));
        assertEquals("36", result.getDecodedFields().get("nas-5gs.sm.5gsm_cause"));
    }

    @Test
    void should_build_structured_tree_for_identity_response() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(
                        new RegistrationRequestNasMessageDissector(),
                        new IdentityResponseNasMessageDissector(),
                        new IdentityRequestNasMessageDissector(),
                        new AuthenticationRequestNasMessageDissector(),
                        new AuthenticationResponseNasMessageDissector(),
                        new SecurityModeCommandNasMessageDissector()
                )
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x5c, 0x00, 0x0d,
                        0x01, 0x00, (byte) 0xf1, 0x10, (byte) 0xf0, (byte) 0xff,
                        0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x10
                })
        );

        assertEquals(0x5c, result.getMessageType());
        assertEquals("Identity response", result.getMessageTypeName());
        assertEquals("13", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.spare_b7"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.suci.supi_fmt"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.spare_b3"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.type_id"));
        assertEquals("001", result.getDecodedFields().get("e212.mcc"));
        assertEquals("01", result.getDecodedFields().get("e212.mnc"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.suci.routing_indicator"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.suci.scheme_id"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.suci.pki"));
        assertEquals("0000000001", result.getDecodedFields().get("nas-5gs.mm.suci.msin"));

        DecodedFieldNode root = result.getFieldTree().get(0);
        DecodedFieldNode messageNode = root.getChildren().stream()
                .filter(node -> "Identity response".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(messageNode);
        DecodedFieldNode mobileIdentityNode = messageNode.getChildren().stream()
                .filter(node -> "5GS mobile identity".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(mobileIdentityNode);
        DecodedFieldNode msinNode = mobileIdentityNode.getChildren().stream()
                .filter(node -> "nas-5gs.mm.suci.msin".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(msinNode);
        assertEquals("0000000001", msinNode.getValue());
    }

    @Test
    void should_build_structured_tree_for_identity_response_with_suci_nai() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new IdentityResponseNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x5c, 0x00, 0x07,
                        0x11,
                        0x61, 0x62, 0x63, 0x40, 0x64, 0x65
                })
        );

        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.suci.supi_fmt"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.type_id"));
        assertEquals("abc@de", result.getDecodedFields().get("nas-5gs.mm.suci.nai"));
    }

    @Test
    void should_build_structured_tree_for_identity_response_with_scheme_output() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new IdentityResponseNasMessageDissector())
        );

        byte[] payload = new byte[57];
        int i = 0;
        payload[i++] = 0x7e;
        payload[i++] = 0x00;
        payload[i++] = 0x5c;
        payload[i++] = 0x00;
        payload[i++] = 0x34;
        payload[i++] = 0x01;
        payload[i++] = 0x00;
        payload[i++] = (byte) 0xf1;
        payload[i++] = 0x10;
        payload[i++] = (byte) 0xf0;
        payload[i++] = (byte) 0xff;
        payload[i++] = 0x01;
        payload[i++] = 0x02;
        for (int n = 1; n <= 32; n++) {
            payload[i++] = (byte) n;
        }
        payload[i++] = (byte) 0xa1;
        payload[i++] = (byte) 0xa2;
        payload[i++] = (byte) 0xa3;
        payload[i++] = (byte) 0xa4;
        payload[i++] = (byte) 0xb1;
        payload[i++] = (byte) 0xb2;
        payload[i++] = (byte) 0xb3;
        payload[i++] = (byte) 0xb4;
        payload[i++] = (byte) 0xb5;
        payload[i++] = (byte) 0xb6;
        payload[i++] = (byte) 0xb7;
        payload[i++] = (byte) 0xb8;

        Nas5gsStructuredDecodeResult result = dissector.dissect(PacketBuffer.wrap(payload));

        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.suci.scheme_id"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.mm.suci.pki"));
        assertEquals(
                "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20a1a2a3a4b1b2b3b4b5b6b7b8",
                result.getDecodedFields().get("nas-5gs.mm.suci.scheme_output")
        );
        assertEquals(
                "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20",
                result.getDecodedFields().get("nas-5gs.mm.suci.scheme_output.ecc_public_key")
        );
        assertEquals(
                "a1a2a3a4",
                result.getDecodedFields().get("nas-5gs.mm.suci.scheme_output.ciphertext")
        );
        assertEquals(
                "b1b2b3b4b5b6b7b8",
                result.getDecodedFields().get("nas-5gs.mm.suci.scheme_output.mac_tag")
        );
    }

    @Test
    void should_build_structured_fields_for_identity_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new IdentityRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{0x7e, 0x00, 0x5b, 0x01})
        );

        assertEquals(0x5b, result.getMessageType());
        assertEquals("Identity request", result.getMessageTypeName());
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.type_id"));
    }

    @Test
    void should_build_structured_fields_for_authentication_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new AuthenticationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x56, 0x02,
                        0x02, 0x00, 0x00,
                        0x21,
                        0x2f, (byte) 0xec, 0x6e, (byte) 0xe6, 0x6b, (byte) 0xcd, (byte) 0xe5, (byte) 0xd6,
                        (byte) 0xa5, (byte) 0x96, 0x6c, (byte) 0x9c, (byte) 0xa0, (byte) 0xca, (byte) 0x80, (byte) 0xdb,
                        0x20, 0x10,
                        0x30, (byte) 0xca, (byte) 0xab, 0x0a, 0x5e, 0x51,
                        (byte) 0x80, 0x00,
                        0x7e, 0x41, 0x0f, 0x6d, (byte) 0xb2, (byte) 0x8e, 0x28, 0x66
                })
        );

        assertEquals(0x56, result.getMessageType());
        assertEquals("Authentication request", result.getMessageTypeName());
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.tsc"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.mm.nas_key_set_id"));
        assertEquals("2", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("00:00", result.getDecodedFields().get("nas-5gs.mm.abba_contents"));
        assertEquals("0x21", result.getDecodedFields().get("gsm_a.dtap.elem_id"));
        assertEquals(
                "2f:ec:6e:e6:6b:cd:e5:d6:a5:96:6c:9c:a0:ca:80:db",
                result.getDecodedFields().get("gsm_a.dtap.rand")
        );
        assertEquals(
                "30:ca:ab:0a:5e:51:80:00:7e:41:0f:6d:b2:8e:28:66",
                result.getDecodedFields().get("gsm_a.dtap.autn")
        );
        assertEquals("30:ca:ab:0a:5e:51", result.getDecodedFields().get("gsm_a.dtap.autn.sqn_xor_ak"));
        assertEquals("80:00", result.getDecodedFields().get("gsm_a.dtap.autn.amf"));
        assertEquals("7e:41:0f:6d:b2:8e:28:66", result.getDecodedFields().get("gsm_a.dtap.autn.mac"));
    }

    @Test
    void should_build_eap_message_node_for_authentication_request() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new AuthenticationRequestNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x56, 0x02,
                        0x02, 0x00, 0x00,
                        0x21,
                        0x2f, (byte) 0xec, 0x6e, (byte) 0xe6, 0x6b, (byte) 0xcd, (byte) 0xe5, (byte) 0xd6,
                        (byte) 0xa5, (byte) 0x96, 0x6c, (byte) 0x9c, (byte) 0xa0, (byte) 0xca, (byte) 0x80, (byte) 0xdb,
                        0x20, 0x10,
                        0x30, (byte) 0xca, (byte) 0xab, 0x0a, 0x5e, 0x51,
                        (byte) 0x80, 0x00,
                        0x7e, 0x41, 0x0f, 0x6d, (byte) 0xb2, (byte) 0x8e, 0x28, 0x66,
                        0x78, 0x00, 0x04, 0x02, 0x01, 0x00, 0x04
                })
        );

        DecodedFieldNode root = result.getFieldTree().get(0);
        DecodedFieldNode messageNode = root.getChildren().stream()
                .filter(node -> "Authentication request".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(messageNode);
        DecodedFieldNode eapNode = messageNode.getChildren().stream()
                .filter(node -> "EAP message".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(eapNode);
        assertEquals("Length", eapNode.getChildren().get(0).getName());
        assertEquals("4", eapNode.getChildren().get(0).getValue());
        assertEquals("Payload", eapNode.getChildren().get(1).getName());
        assertEquals("02010004", eapNode.getChildren().get(1).getValue());
    }

    @Test
    void should_build_structured_fields_for_authentication_response() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new AuthenticationResponseNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x57, 0x2d, 0x10,
                        (byte) 0xf4, 0x12, (byte) 0xb4, (byte) 0x99,
                        (byte) 0xc3, 0x71, (byte) 0xbf, 0x6a,
                        (byte) 0xac, (byte) 0xf0, 0x43, (byte) 0xd8,
                        0x19, (byte) 0xb1, (byte) 0x91, (byte) 0xd8
                })
        );

        assertEquals(0x57, result.getMessageType());
        assertEquals("Authentication response", result.getMessageTypeName());
        assertEquals("45", result.getDecodedFields().get("nas-eps.emm.elem_id"));
        assertEquals("16", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("f412b499c371bf6aacf043d819b191d8", result.getDecodedFields().get("nas-eps.emm.res"));
    }

    @Test
    void should_build_eap_message_node_for_authentication_response() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new AuthenticationResponseNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x00, 0x57, 0x2d, 0x10,
                        (byte) 0xf4, 0x12, (byte) 0xb4, (byte) 0x99,
                        (byte) 0xc3, 0x71, (byte) 0xbf, 0x6a,
                        (byte) 0xac, (byte) 0xf0, 0x43, (byte) 0xd8,
                        0x19, (byte) 0xb1, (byte) 0x91, (byte) 0xd8,
                        0x78, 0x00, 0x04, 0x02, 0x02, 0x00, 0x04
                })
        );

        DecodedFieldNode root = result.getFieldTree().get(0);
        DecodedFieldNode messageNode = root.getChildren().stream()
                .filter(node -> "Authentication response".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(messageNode);
        DecodedFieldNode eapNode = messageNode.getChildren().stream()
                .filter(node -> "EAP message".equals(node.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(eapNode);
        assertEquals("4", eapNode.getChildren().get(0).getValue());
        assertEquals("02020004", eapNode.getChildren().get(1).getValue());
    }

    @Test
    void should_build_structured_fields_for_security_mode_command_inside_protected_wrapper() {
        Nas5gsStructuredDissector dissector = new Nas5gsStructuredDissector(
                List.of(new SecurityModeCommandNasMessageDissector())
        );

        Nas5gsStructuredDecodeResult result = dissector.dissect(
                PacketBuffer.wrap(new byte[]{
                        0x7e, 0x04,
                        0x11, 0x22, 0x33, 0x44,
                        0x09,
                        0x7e, 0x00, 0x5d,
                        0x12,
                        0x02,
                        0x04,
                        (byte) 0xf0,
                        0x70,
                        0x00,
                        0x00
                })
        );

        assertEquals(0x5d, result.getMessageType());
        assertEquals("Security mode command", result.getMessageTypeName());
        assertEquals("11223344", result.getDecodedFields().get("nas-5gs.msg_auth_code"));
        assertEquals("9", result.getDecodedFields().get("nas-5gs.seq_no"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.nas_sec_algo_enc"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.mm.nas_sec_algo_ip"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.tsc"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.mm.nas_key_set_id"));
        assertEquals("4", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5g_ea0"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.128_5g_ea1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.128_5g_ea2"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.128_5g_ea3"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ea4"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ea5"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ea6"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ea7"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.ia0"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5g_128_ia1"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5g_128_ia2"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5g_128_ia3"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_128_ia4"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ia5"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ia6"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.5g_ia7"));
    }

    private void assertNodeOrder(DecodedFieldNode messageNode, String... names) {
        int lastIndex = -1;
        for (String name : names) {
            int currentIndex = findChildIndex(messageNode, name);
            assertFalse(currentIndex < 0, "missing node " + name);
            assertFalse(currentIndex <= lastIndex, "node order mismatch for " + name);
            lastIndex = currentIndex;
        }
    }

    private int findChildIndex(DecodedFieldNode node, String childName) {
        for (int i = 0; i < node.getChildren().size(); i++) {
            if (childName.equals(node.getChildren().get(i).getName())) {
                return i;
            }
        }
        return -1;
    }
}
