#include "config.h"
#include "wireshark.h"

#include <epan/prefs.h>
#include <epan/packet.h>
#include <epan/proto.h>
#include <epan/column-info.h>
#include <epan/addr_resolv.h>
#include <epan/dfilter/dfilter.h>
#include <epan/expert.h>
#include <epan/ftypes/ftypes-int.h>
#include <epan/in_cksum.h>
#include <epan/oids.h>
#include <epan/osi-utils.h>
#include <epan/prefs-int.h>
#include <epan/reassemble.h>
#include <epan/stream.h>
#include <epan/stats_tree.h>
#include <epan/tap.h>
#include <epan/uat.h>
#include <epan/uuid_types.h>
#include <wsutil/regex.h>
#include <epan/dissectors/packet-gsm_a_common.h>
#include <epan/dissectors/packet-mac-nr.h>
#include <epan/dissectors/packet-pdcp-nr.h>
#include <epan/dissectors/packet-rlc-nr.h>
#include <epan/dissectors/packet-tcp.h>
#include <gcrypt.h>
#include <wiretap/wtap.h>
#include <wsutil/inet_cidr.h>
#include <stdarg.h>

static module_t *ws_dummy_module = (module_t *) 0x1;
static expert_module_t *ws_dummy_expert_module = (expert_module_t *) 0x1;
static conversation_t *ws_dummy_conversation = (conversation_t *) 0x1;
static stats_tree_cfg *ws_dummy_stats_tree_cfg = (stats_tree_cfg *) 0x1;
static int proto_ws_core_shared_fields = -1;

static const value_string ws_empty_ext_vals[] = {
    { 0, NULL }
};

e_prefs prefs;
e_addr_resolve gbl_resolv_flags;

char *g_ethers_path = NULL;
char *g_ipxnets_path = NULL;
char *g_pethers_path = NULL;
char *g_pipxnets_path = NULL;

int hf_3gpp_tmsi = -1;
int hf_gsm_a_L3_protocol_discriminator = -1;
int proto_mac_nr = -1;
int proto_rlc_nr = -1;
int proto_pdcp_nr = -1;
value_string_ext lte_rrc_messageIdentifier_vals_ext = VALUE_STRING_EXT_INIT(ws_empty_ext_vals);

const value_string etype_vals[] = {
    { 0, NULL }
};

const value_string protocol_discriminator_vals[] = {
    { 0, NULL }
};

const value_string ssCode_vals[] = {
    { 0, NULL }
};

const value_string expert_group_vals[] = {
    { 0, "group" },
    { 0, NULL }
};

const value_string expert_severity_vals[] = {
    { 0, "severity" },
    { 0, NULL }
};

const value_string expert_checksum_vals[] = {
    { 0, "checksum" },
    { 0, NULL }
};

const reassembly_table_functions addresses_reassembly_table_functions = { 0 };
const reassembly_table_functions addresses_ports_reassembly_table_functions = { 0 };

void
proto_register_ws_core_shared_fields(void)
{
    static hf_register_info hf[] = {
        { &hf_3gpp_tmsi, { "5G-TMSI", "nas-5gs.5g_tmsi", FT_UINT32, BASE_HEX, NULL, 0x0, NULL, HFILL } },
    };

    if (proto_ws_core_shared_fields != -1) {
        return;
    }

    proto_ws_core_shared_fields = proto_register_protocol(
        "WS Core Shared Fields",
        "WS_CORE_SHARED",
        "ws_core_shared");
    proto_register_field_array(proto_ws_core_shared_fields, hf, G_N_ELEMENTS(hf));
}

uint8_t
dissect_cbs_data_coding_scheme(tvbuff_t *tvb _U_, packet_info *pinfo _U_, proto_tree *tree _U_, uint16_t offset _U_)
{
    return 2;
}

tvbuff_t *
dissect_cbs_data(uint8_t sms_encoding _U_, tvbuff_t *tvb, proto_tree *tree _U_, packet_info *pinfo _U_, unsigned offset)
{
    int length = tvb_reported_length_remaining(tvb, (int) offset);
    if (length <= 0) {
        return NULL;
    }
    return tvb_new_subset_length(tvb, offset, (unsigned) length);
}

const char *
http2_get_stream_imsi(packet_info *pinfo _U_)
{
    return NULL;
}

void
gtp_add_teid_imsi(uint32_t teid _U_, const char *imsi _U_)
{
}

void
report_failure(const char *format _U_, ...)
{
}

bool
value_is_in_range(const range_t *range, uint32_t val _U_)
{
    return range != NULL && range->nranges > 0;
}

const char *
tvb_ntp_fmt_ts_sec(wmem_allocator_t *allocator, tvbuff_t *tvb _U_, int offset _U_)
{
    return wmem_strdup(allocator, "0");
}

void
ntp_to_nstime(tvbuff_t *tvb _U_, int offset _U_, nstime_t *nstime)
{
    if (nstime != NULL) {
        nstime->secs = 0;
        nstime->nsecs = 0;
    }
}

#define WS_STUB_PDU_DISSECTOR(name) \
int name(tvbuff_t *tvb _U_, packet_info *pinfo _U_, proto_tree *tree _U_, void *data _U_) \
{ \
    return tvb == NULL ? 0 : tvb_captured_length(tvb); \
}

#if !defined(WS_CORE_NR_RRC_MINIMAL_BUILD) && !defined(WS_CORE_MAC_NR_CHAIN_MINIMAL_BUILD)
WS_STUB_PDU_DISSECTOR(dissect_nr_rrc_HandoverCommand_PDU)
WS_STUB_PDU_DISSECTOR(dissect_nr_rrc_HandoverPreparationInformation_PDU)
WS_STUB_PDU_DISSECTOR(dissect_nr_rrc_IntendedServiceAreaInfo_areaCoordinates_r19_PDU)
WS_STUB_PDU_DISSECTOR(dissect_nr_rrc_nr_RLF_Report_r16_PDU)
WS_STUB_PDU_DISSECTOR(dissect_nr_rrc_ReferenceTime_r16_PDU)
WS_STUB_PDU_DISSECTOR(dissect_nr_rrc_SuccessHO_Report_r17_PDU)
WS_STUB_PDU_DISSECTOR(dissect_nr_rrc_SuccessPSCell_Report_r18_PDU)
WS_STUB_PDU_DISSECTOR(dissect_nr_rrc_VisitedCellInfoList_r16_PDU)
#endif
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_HandoverCommand_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_HandoverPreparationInformation_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_MeasResultList3EUTRA_r15_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_MeasResultSCG_FailureMRDC_r15_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_RLF_Report_r9_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_RLF_Report_v9e0_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_SidelinkUEInformation_r12_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_SL_Parameters_v1430_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_SL_Parameters_v1530_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_SL_Parameters_v1540_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_SystemInformationBlockType21_r14_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_TDD_Config_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_UE_EUTRA_Capability_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_UEAssistanceInformation_r11_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_UECapabilityEnquiry_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_UEPagingCoverageInformation_NB_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_UEPagingCoverageInformation_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_V2X_BandParameters_r14_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lte_rrc_V2X_BandParameters_v1530_PDU)
WS_STUB_PDU_DISSECTOR(dissect_gsm_map_lcs_LCS_ClientID_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lcsap_Correlation_ID_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lpp_AssistanceDataSIBelement_r15_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lpp_DisplacementTimeStamp_r15_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lpp_Ellipsoid_Point_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lpp_LocationCoordinates_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lpp_LocationError_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lpp_LocationSource_r13_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lpp_Polygon_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lpp_Sensor_MeasurementInformation_r13_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lpp_Sensor_MotionInformation_r15_PDU)
WS_STUB_PDU_DISSECTOR(dissect_lpp_Velocity_PDU)
WS_STUB_PDU_DISSECTOR(dissect_NRPPa_PDU_PDU)
WS_STUB_PDU_DISSECTOR(dissect_ranap_LastVisitedUTRANCell_Item_PDU)
WS_STUB_PDU_DISSECTOR(dissect_rrc_HandoverToUTRANCommand_PDU)
WS_STUB_PDU_DISSECTOR(dissect_rrc_InterRATHandoverInfo_PDU)
WS_STUB_PDU_DISSECTOR(dissect_s1ap_EN_DCSONConfigurationTransfer_PDU)
WS_STUB_PDU_DISSECTOR(dissect_s1ap_LastVisitedEUTRANCellInformation_PDU)
WS_STUB_PDU_DISSECTOR(dissect_s1ap_LastVisitedGERANCellInformation_PDU)
WS_STUB_PDU_DISSECTOR(dissect_s1ap_MDTMode_PDU)
WS_STUB_PDU_DISSECTOR(dissect_s1ap_SourceeNB_ToTargeteNB_TransparentContainer_PDU)
WS_STUB_PDU_DISSECTOR(dissect_s1ap_TargeteNB_ToSourceeNB_TransparentContainer_PDU)
WS_STUB_PDU_DISSECTOR(dissect_rrc_ToTargetRNC_Container_PDU)
WS_STUB_PDU_DISSECTOR(dissect_rrc_TargetRNC_ToSourceRNC_Container_PDU)

#undef WS_STUB_PDU_DISSECTOR

module_t *
prefs_register_protocol(int id _U_, void (*apply_cb)(void) _U_)
{
    return ws_dummy_module;
}

void
prefs_register_bool_preference(module_t *module _U_, const char *name _U_,
    const char *title _U_, const char *description _U_, bool *var _U_)
{
}

void
prefs_register_enum_preference(module_t *module _U_, const char *name _U_,
    const char *title _U_, const char *description _U_, int *var _U_,
    const enum_val_t *enumvals _U_, bool radio_buttons _U_)
{
}

void
prefs_register_dissector_preference(module_t *module _U_, const char *name _U_,
    const char *title _U_, const char *description _U_, const char **var _U_)
{
}

void
prefs_register_string_preference(module_t *module _U_, const char *name _U_,
    const char *title _U_, const char *description _U_, const char **var _U_)
{
}

void
prefs_register_uat_preference(module_t *module _U_, const char *name _U_,
    const char *title _U_, const char *description _U_, struct epan_uat *uat _U_)
{
}

void
prefs_register_obsolete_preference(module_t *module _U_, const char *name _U_)
{
}

module_t *
prefs_find_module(const char *name _U_)
{
    return ws_dummy_module;
}

pref_t *
prefs_find_preference(module_t *module _U_, const char *name _U_)
{
    return NULL;
}

bool
prefs_get_bool_value(pref_t *pref _U_, pref_source_t source _U_)
{
    return false;
}

range_t *
prefs_get_range_value(const char *module_name _U_, const char *pref_name _U_)
{
    return range_empty(wmem_epan_scope());
}

void
prefs_register_decode_as_range_preference(module_t *module _U_, const char *name _U_,
    const char *title _U_, const char *description _U_, range_t **var _U_,
    uint32_t max_value _U_, const char *dissector_table _U_, const char *dissector_description _U_)
{
}

range_t *
range_empty(wmem_allocator_t *scope)
{
    range_t *range = wmem_alloc(scope, sizeof(range_t));
    range->nranges = 0;
    return range;
}

convert_ret_t
range_convert_str(wmem_allocator_t *scope, range_t **range, const char *es _U_, uint32_t max_value _U_)
{
    if (range != NULL) {
        *range = range_empty(scope);
    }
    return CVT_NO_ERROR;
}

char *
range_convert_range(wmem_allocator_t *scope, const range_t *range _U_)
{
    return wmem_strdup(scope, "");
}

range_t *
range_copy(wmem_allocator_t *scope, const range_t *src)
{
    if (src == NULL) {
        return range_empty(scope);
    }
    range_t *copy = wmem_alloc(scope, sizeof(range_t) + sizeof(range_admin_t) * src->nranges);
    memcpy(copy, src, sizeof(range_t) + sizeof(range_admin_t) * src->nranges);
    return copy;
}

uint16_t
de_sm_tflow_temp(tvbuff_t *tvb _U_, proto_tree *tree _U_, packet_info *pinfo _U_,
    uint32_t offset _U_, unsigned len _U_, char *add_string _U_, int string_len _U_)
{
    return 0;
}

uint16_t
de_lai(tvbuff_t *tvb _U_, proto_tree *tree _U_, packet_info *pinfo _U_,
    uint32_t offset _U_, unsigned len, char *add_string _U_, int string_len _U_)
{
    return (uint16_t) len;
}

uint16_t
de_mid(tvbuff_t *tvb _U_, proto_tree *tree _U_, packet_info *pinfo _U_,
    uint32_t offset _U_, unsigned len, char *add_string _U_, int string_len _U_)
{
    return (uint16_t) len;
}

uint16_t
de_ms_cm_2(tvbuff_t *tvb _U_, proto_tree *tree _U_, packet_info *pinfo _U_,
    uint32_t offset _U_, unsigned len, char *add_string _U_, int string_len _U_)
{
    return (uint16_t) len;
}

uint16_t
de_ms_cm_3(tvbuff_t *tvb _U_, proto_tree *tree _U_, packet_info *pinfo _U_,
    uint32_t offset _U_, unsigned len, char *add_string _U_, int string_len _U_)
{
    return (uint16_t) len;
}

uint16_t
de_plmn_list(tvbuff_t *tvb _U_, proto_tree *tree _U_, packet_info *pinfo _U_,
    uint32_t offset _U_, unsigned len, char *add_string _U_, int string_len _U_)
{
    return (uint16_t) len;
}

uint16_t
de_sm_pco(tvbuff_t *tvb _U_, proto_tree *tree _U_, packet_info *pinfo _U_,
    uint32_t offset _U_, unsigned len _U_, char *add_string _U_, int string_len _U_)
{
    return (uint16_t) len;
}

void
tcp_dissect_pdus(tvbuff_t *tvb _U_, packet_info *pinfo _U_, proto_tree *tree _U_,
    bool proto_desegment _U_, unsigned fixed_len _U_,
    unsigned (*get_pdu_len)(packet_info *, tvbuff_t *, int, void *) _U_,
    dissector_t dissect_pdu _U_, void *dissector_data _U_)
{
}

void
dfilter_init(const char *app_env_var_prefix _U_)
{
}

void
dfilter_cleanup(void)
{
}

void
dfilter_translator_init(void)
{
}

void
dfilter_translator_cleanup(void)
{
}

void
dfilter_free(dfilter_t *df _U_)
{
}

bool
dfilter_apply_full(dfilter_t *df _U_, proto_tree *tree _U_, GPtrArray **fvals)
{
    if (fvals != NULL) {
        *fvals = NULL;
    }
    return false;
}

bool
dfilter_apply_edt(dfilter_t *df _U_, struct epan_dissect *edt _U_)
{
    return false;
}

void
dfilter_prime_proto_tree(const dfilter_t *df _U_, proto_tree *tree _U_)
{
}

void
dfilter_prime_proto_tree_print(const dfilter_t *df _U_, proto_tree *tree _U_)
{
}

void
uuid_types_initialize(void)
{
}

void
addr_resolv_init(const char *app_env_var_prefix _U_)
{
}

void
addr_resolv_cleanup(void)
{
}

void
host_name_lookup_reset(const char *app_env_var_prefix _U_)
{
}

void
wtap_block_unref(wtap_block_t block _U_)
{
}

void
address_types_initialize(void)
{
}

const char *
get_current_working_dir(void)
{
    return ".";
}

void
disable_name_resolution(void)
{
    memset(&gbl_resolv_flags, 0, sizeof(gbl_resolv_flags));
}

bool
host_name_lookup_process(void)
{
    return false;
}

bool
get_host_ipaddr(const char *host, uint32_t *addrp)
{
    unsigned int a, b, c, d;
    if (host == NULL || addrp == NULL) {
        return false;
    }
    if (sscanf(host, "%u.%u.%u.%u", &a, &b, &c, &d) != 4) {
        return false;
    }
    if (a > 255 || b > 255 || c > 255 || d > 255) {
        return false;
    }
    *addrp = g_htonl(((a & 0xFFu) << 24) | ((b & 0xFFu) << 16) | ((c & 0xFFu) << 8) | (d & 0xFFu));
    return true;
}

bool
get_host_ipaddr6(const char *host, ws_in6_addr *addrp)
{
    if (host == NULL || addrp == NULL) {
        return false;
    }
    return ws_inet_pton6(host, addrp);
}

const char *
get_hostname(const unsigned addr)
{
    return wmem_strdup_printf(NULL, "%u.%u.%u.%u",
        (addr >> 24) & 0xFF, (addr >> 16) & 0xFF, (addr >> 8) & 0xFF, addr & 0xFF);
}

char *
get_hostname_wmem(wmem_allocator_t *allocator, const unsigned addr)
{
    return wmem_strdup_printf(allocator, "%u.%u.%u.%u",
        (addr >> 24) & 0xFF, (addr >> 16) & 0xFF, (addr >> 8) & 0xFF, addr & 0xFF);
}

const char *
get_hostname6(const ws_in6_addr *ad)
{
    char buf[WS_INET6_ADDRSTRLEN];
    ws_inet_ntop6(ad, buf, sizeof(buf));
    return wmem_strdup(NULL, buf);
}

char *
get_hostname6_wmem(wmem_allocator_t *allocator, const ws_in6_addr *ad)
{
    char buf[WS_INET6_ADDRSTRLEN];
    ws_inet_ntop6(ad, buf, sizeof(buf));
    return wmem_strdup(allocator, buf);
}

const char *
get_ether_name(const uint8_t *addr)
{
    return wmem_strdup_printf(NULL, "%02x:%02x:%02x:%02x:%02x:%02x",
        addr[0], addr[1], addr[2], addr[3], addr[4], addr[5]);
}

const char *
tvb_get_ether_name(tvbuff_t *tvb, unsigned offset)
{
    const uint8_t *addr = tvb_get_ptr(tvb, offset, 6);
    return get_ether_name(addr);
}

const char *
get_ether_name_if_known(const uint8_t *addr _U_)
{
    return NULL;
}

const char *
get_manuf_name(const uint8_t *addr, size_t size)
{
    if (size >= 3) {
        return wmem_strdup_printf(NULL, "%02x:%02x:%02x", addr[0], addr[1], addr[2]);
    }
    return "";
}

const char *
get_manuf_name_if_known(const uint8_t *addr _U_, size_t size _U_)
{
    return NULL;
}

const char *
uint_get_manuf_name_if_known(const uint32_t oid _U_)
{
    return NULL;
}

const char *
tvb_get_manuf_name(tvbuff_t *tvb, unsigned offset)
{
    const uint8_t *addr = tvb_get_ptr(tvb, offset, 3);
    return get_manuf_name(addr, 3);
}

const char *
tvb_get_manuf_name_if_known(tvbuff_t *tvb _U_, unsigned offset _U_)
{
    return NULL;
}

const char *
get_eui64_name(const uint8_t *addr)
{
    return wmem_strdup_printf(NULL, "%02x:%02x:%02x:%02x:%02x:%02x:%02x:%02x",
        addr[0], addr[1], addr[2], addr[3], addr[4], addr[5], addr[6], addr[7]);
}

char *
eui64_to_display(wmem_allocator_t *allocator, const uint64_t addr)
{
    return wmem_strdup_printf(allocator, "%02x:%02x:%02x:%02x:%02x:%02x:%02x:%02x",
        (unsigned)((addr >> 56) & 0xFF), (unsigned)((addr >> 48) & 0xFF),
        (unsigned)((addr >> 40) & 0xFF), (unsigned)((addr >> 32) & 0xFF),
        (unsigned)((addr >> 24) & 0xFF), (unsigned)((addr >> 16) & 0xFF),
        (unsigned)((addr >> 8) & 0xFF), (unsigned)(addr & 0xFF));
}

char *
get_ipxnet_name(wmem_allocator_t *allocator, const uint32_t addr)
{
    return wmem_strdup_printf(allocator, "%08X", addr);
}

char *
get_vlan_name(wmem_allocator_t *allocator, const uint16_t id)
{
    return wmem_strdup_printf(allocator, "%u", id);
}

static char *
ws_address_numeric_to_str(wmem_allocator_t *scope, const address *addr)
{
    if (addr == NULL) {
        return wmem_strdup(scope, "<null>");
    }

    switch (addr->type) {
    case AT_NONE:
        return wmem_strdup(scope, "NONE");
    case AT_IPv4:
        if (addr->data != NULL && addr->len >= 4) {
            const uint8_t *p = (const uint8_t *)addr->data;
            return wmem_strdup_printf(scope, "%u.%u.%u.%u", p[0], p[1], p[2], p[3]);
        }
        return wmem_strdup(scope, "0.0.0.0");
    case AT_IPv6:
        if (addr->data != NULL && addr->len >= 16) {
            char buf[WS_INET6_ADDRSTRLEN];
            ws_inet_ntop6(addr->data, buf, sizeof(buf));
            return wmem_strdup(scope, buf);
        }
        return wmem_strdup(scope, "::");
    case AT_ETHER:
        if (addr->data != NULL && addr->len >= 6) {
            const uint8_t *p = (const uint8_t *)addr->data;
            return wmem_strdup_printf(scope, "%02x:%02x:%02x:%02x:%02x:%02x",
                p[0], p[1], p[2], p[3], p[4], p[5]);
        }
        return wmem_strdup(scope, "00:00:00:00:00:00");
    case AT_EUI64:
    case AT_FC:
    case AT_FCWWN:
        if (addr->data != NULL && addr->len > 0) {
            const uint8_t *p = (const uint8_t *)addr->data;
            GString *s = g_string_new(NULL);
            for (int i = 0; i < addr->len; i++) {
                if (i > 0) {
                    g_string_append_c(s, ':');
                }
                g_string_append_printf(s, "%02x", p[i]);
            }
            return g_string_free(s, FALSE);
        }
        return wmem_strdup(scope, "");
    default:
        return wmem_strdup_printf(scope, "<addr type=%d len=%d>", addr->type, addr->len);
    }
}

char *
address_to_str(wmem_allocator_t *scope, const address *addr)
{
    return ws_address_numeric_to_str(scope, addr);
}

char *
address_with_resolution_to_str(wmem_allocator_t *scope, const address *addr)
{
    return ws_address_numeric_to_str(scope, addr);
}

const char *
address_to_name(const address *addr _U_)
{
    return NULL;
}

char *
address_to_display(wmem_allocator_t *allocator, const address *addr)
{
    return ws_address_numeric_to_str(allocator, addr);
}

void
address_to_str_buf(const address *addr, char *buf, int buf_len)
{
    char *s = ws_address_numeric_to_str(NULL, addr);
    g_strlcpy(buf, s, buf_len);
}

char *
tvb_address_to_str(wmem_allocator_t *scope, tvbuff_t *tvb, int type, const unsigned offset)
{
    address addr = ADDRESS_INIT_NONE;
    unsigned len = 0;
    switch (type) {
    case AT_IPv4: len = 4; break;
    case AT_IPv6: len = 16; break;
    case AT_ETHER: len = 6; break;
    case AT_EUI64:
    case AT_FC:
    case AT_FCWWN: len = 8; break;
    default: len = 0; break;
    }
    set_address_tvb(&addr, type, len, tvb, offset);
    return address_to_str(scope, &addr);
}

char *
tvb_address_with_resolution_to_str(wmem_allocator_t *scope, tvbuff_t *tvb, int type, const unsigned offset)
{
    return tvb_address_to_str(scope, tvb, type, offset);
}

const char *
serv_name_lookup(port_type proto _U_, unsigned port)
{
    return wmem_strdup_printf(NULL, "%u", port);
}

const char *
try_serv_name_lookup(port_type proto _U_, unsigned port _U_)
{
    return NULL;
}

char *
port_with_resolution_to_str(wmem_allocator_t *scope, port_type proto _U_, unsigned port)
{
    return wmem_strdup_printf(scope, "%u", port);
}

int
port_with_resolution_to_str_buf(char *buf, unsigned long buf_size, port_type proto _U_, unsigned port)
{
    return g_snprintf(buf, buf_size, "%u", port);
}

char *
oid_resolved(wmem_allocator_t *scope, unsigned len, uint32_t *subids)
{
    GString *s = g_string_new(NULL);
    for (unsigned i = 0; i < len; i++) {
        if (i > 0) {
            g_string_append_c(s, '.');
        }
        g_string_append_printf(s, "%u", subids[i]);
    }
    if (scope != NULL) {
        char *result = wmem_strdup(scope, s->str);
        g_string_free(s, TRUE);
        return result;
    }
    return g_string_free(s, FALSE);
}

char *
oid_resolved_from_encoded(wmem_allocator_t *scope, const uint8_t *oid, int len)
{
    GString *s = g_string_new(NULL);
    for (int i = 0; i < len; i++) {
        if (i > 0) {
            g_string_append_c(s, '.');
        }
        g_string_append_printf(s, "%u", oid[i]);
    }
    if (scope != NULL) {
        char *result = wmem_strdup(scope, s->str);
        g_string_free(s, TRUE);
        return result;
    }
    return g_string_free(s, FALSE);
}

char *
rel_oid_resolved_from_encoded(wmem_allocator_t *scope, const uint8_t *oid, int len)
{
    return oid_resolved_from_encoded(scope, oid, len);
}

char *
oid_resolved_from_string(wmem_allocator_t *scope, const char *oid_str)
{
    return wmem_strdup(scope, oid_str != NULL ? oid_str : "");
}

void
oid_add_from_string(const char *name _U_, const char *oid_str _U_)
{
}

char *
oid_encoded2string(wmem_allocator_t *scope, const uint8_t* encoded, unsigned len)
{
    return oid_resolved_from_encoded(scope, encoded, (int)len);
}

char *
rel_oid_encoded2string(wmem_allocator_t *scope, const uint8_t* encoded, unsigned len)
{
    return rel_oid_resolved_from_encoded(scope, encoded, (int)len);
}

char *
oid_subid2string(wmem_allocator_t *scope, uint32_t *subids, unsigned len)
{
    return oid_resolved(scope, len, subids);
}

char *
rel_oid_subid2string(wmem_allocator_t *scope, uint32_t *subids, unsigned len, bool is_absolute _U_)
{
    return oid_resolved(scope, len, subids);
}

char *
print_system_id(wmem_allocator_t *scope, const uint8_t *data, int length)
{
    GString *s = g_string_new(NULL);
    for (int i = 0; i < length; i++) {
        if (i > 0) {
            g_string_append_c(s, '.');
        }
        g_string_append_printf(s, "%02x", data[i]);
    }
    if (scope != NULL) {
        char *result = wmem_strdup(scope, s->str);
        g_string_free(s, TRUE);
        return result;
    }
    return g_string_free(s, FALSE);
}

char *
tvb_print_system_id(wmem_allocator_t *scope, tvbuff_t *tvb, const int offset, int length)
{
    return print_system_id(scope, tvb_get_ptr(tvb, offset, length), length);
}

void
print_system_id_buf(const uint8_t *data, int length, char *buf, int buf_len)
{
    char *s = print_system_id(NULL, data, length);
    g_strlcpy(buf, s, buf_len);
}

void
stream_init(void)
{
}

void
stream_cleanup(void)
{
}

void
tap_register_plugin(const tap_plugin *plug _U_)
{
}

void
register_all_tap_listeners(tap_reg_t const *tap_reg_listeners _U_)
{
}

void
tap_init(void)
{
}

int
register_tap(const char *name _U_)
{
    return 0;
}

GList *
get_tap_names(void)
{
    return NULL;
}

int
find_tap_id(const char *name _U_)
{
    return 0;
}

void
tap_queue_packet(int tap_id _U_, packet_info *pinfo _U_, const void *tap_specific_data _U_)
{
}

void
tap_build_interesting(epan_dissect_t *edt _U_)
{
}

void
tap_queue_init(epan_dissect_t *edt _U_)
{
}

void
tap_push_tapped_queue(epan_dissect_t *edt _U_)
{
}

void
reset_tap_listeners(void)
{
}

void
draw_tap_listeners(bool draw_all _U_)
{
}

GString *
register_tap_listener(const char *tapname _U_, void *tapdata _U_, const char *fstring _U_,
    unsigned flags _U_, tap_reset_cb tap_reset _U_, tap_packet_cb tap_packet _U_,
    tap_draw_cb tap_draw _U_, tap_finish_cb tap_finish _U_)
{
    return NULL;
}

GString *
set_tap_dfilter(void *tapdata _U_, const char *fstring _U_)
{
    return NULL;
}

void
tap_listeners_dfilter_recompile(void)
{
}

void
remove_tap_listener(void *tapdata _U_)
{
}

GString *
set_tap_flags(void *tapdata _U_, unsigned flags _U_)
{
    return NULL;
}

bool
tap_listeners_require_dissection(void)
{
    return false;
}

bool
tap_listeners_require_columns(void)
{
    return false;
}

bool
have_tap_listener(int tap_id _U_)
{
    return false;
}

bool
have_filtering_tap_listeners(void)
{
    return false;
}

void
tap_listeners_load_field_references(epan_dissect_t *edt _U_)
{
}

unsigned
union_of_tap_listener_flags(void)
{
    return 0;
}

const void *
fetch_tapped_data(int tap_id _U_, int idx _U_)
{
    return NULL;
}

void
tap_cleanup(void)
{
}

void
tap_load_main_filter(struct epan_dfilter *dfcode _U_)
{
}

ws_regex_t *
ws_regex_compile(const char *patt _U_, char **errmsg)
{
    if (errmsg != NULL) {
        *errmsg = NULL;
    }
    return NULL;
}

ws_regex_t *
ws_regex_compile_ex(const char *patt _U_, ssize_t size _U_, char **errmsg, unsigned flags _U_)
{
    if (errmsg != NULL) {
        *errmsg = NULL;
    }
    return NULL;
}

bool
ws_regex_matches(const ws_regex_t *re _U_, const char *subj _U_)
{
    return false;
}

bool
ws_regex_matches_length(const ws_regex_t *re _U_, const char *subj _U_, ssize_t subj_length _U_)
{
    return false;
}

bool
ws_regex_matches_pos(const ws_regex_t *re _U_, const char *subj _U_, ssize_t subj_length _U_,
    size_t subj_offset _U_, size_t pos_vect[2])
{
    if (pos_vect != NULL) {
        pos_vect[0] = 0;
        pos_vect[1] = 0;
    }
    return false;
}

void
ws_regex_free(ws_regex_t *re _U_)
{
}

const char *
ws_regex_pattern(const ws_regex_t *re _U_)
{
    return "";
}

int
uuid_type_dissector_register(const char* name _U_, GHashFunc hash_func _U_, GEqualFunc equal_func _U_,
    const char* (*uuid_to_str)(void*, wmem_allocator_t*) _U_)
{
    return 0;
}

int
uuid_type_get_id_by_name(const char* name _U_)
{
    return 0;
}

void
uuid_type_foreach(const char* name _U_, GHFunc func _U_, void* param _U_)
{
}

void
uuid_type_foreach_by_id(int id _U_, GHFunc func _U_, void* param _U_)
{
}

void
uuid_type_insert(int id _U_, void* uuid _U_, void* value _U_)
{
}

void*
uuid_type_lookup(int id _U_, void* uuid _U_)
{
    return NULL;
}

bool
uuid_type_remove_if_present(int id _U_, void* uuid _U_)
{
    return false;
}

const char*
uuid_type_get_uuid_name(const char* name _U_, void* uuid _U_, wmem_allocator_t* scope _U_)
{
    return NULL;
}

uint16_t
in_cksum_shouldbe(uint16_t sum, uint16_t computed_sum)
{
    uint32_t shouldbe = (uint32_t)sum + (uint32_t)computed_sum;
    shouldbe = (shouldbe & 0xFFFFu) + (shouldbe >> 16);
    shouldbe = (shouldbe & 0xFFFFu) + (shouldbe >> 16);
    return (uint16_t)~shouldbe;
}

uint32_t
ws_ipv4_get_subnet_mask(const uint32_t mask_length)
{
    if (mask_length == 0) {
        return 0;
    }
    if (mask_length >= 32) {
        return 0xFFFFFFFFu;
    }
    return 0xFFFFFFFFu << (32 - mask_length);
}

void
ws_ipv4_addr_and_mask_init(ipv4_addr_and_mask *dst, ws_in4_addr src_addr, unsigned src_bits)
{
    uint32_t mask = ws_ipv4_get_subnet_mask(src_bits);
    dst->addr = src_addr & mask;
    dst->nmask = mask;
}

bool
ws_ipv4_addr_and_mask_contains(const ipv4_addr_and_mask *ipv4, const ws_in4_addr *addr)
{
    return ((*addr) & ipv4->nmask) == ipv4->addr;
}

void
ws_vadd_crash_info(const char *fmt _U_, va_list ap _U_)
{
}

void
ws_add_crash_info(const char *fmt _U_, ...)
{
    va_list ap;
    va_start(ap, fmt);
    ws_vadd_crash_info(fmt, ap);
    va_end(ap);
}

void
prefs_init(const char **col_fmt _U_, int num_cols _U_)
{
}

void
prefs_cleanup(void)
{
}

e_prefs *
read_prefs(const char *app_env_var_prefix _U_)
{
    return &prefs;
}

void
secrets_init(void)
{
}

void
secrets_cleanup(void)
{
}

void
keytab_file_data_init(void)
{
}

void
capture_dissector_init(void)
{
}

void
capture_dissector_cleanup(void)
{
}

void
reassembly_tables_init(void)
{
}

void
reassembly_table_register(reassembly_table *table _U_, const reassembly_table_functions *funcs _U_)
{
}

void
reassembly_table_init(reassembly_table *table, const reassembly_table_functions *funcs _U_)
{
    if (table != NULL) {
        table->fragment_table = NULL;
        table->reassembled_table = NULL;
        table->temporary_key_func = NULL;
        table->persistent_key_func = NULL;
        table->free_temporary_key_func = NULL;
    }
}

void
reassembly_table_destroy(reassembly_table *table _U_)
{
}

void
reassembly_table_cleanup(void)
{
}

fragment_head *
fragment_add_seq_check(reassembly_table *table _U_, tvbuff_t *tvb _U_, const int offset _U_,
    const packet_info *pinfo _U_, const uint32_t id _U_, const void *data _U_,
    const uint32_t frag_number _U_, const uint32_t frag_data_len _U_, const bool more_frags _U_)
{
    return NULL;
}

fragment_head *
fragment_add(reassembly_table *table _U_, tvbuff_t *tvb _U_, const int offset _U_,
    const packet_info *pinfo _U_, const uint32_t id _U_, const void *data _U_,
    const uint32_t frag_offset _U_, const uint32_t frag_data_len _U_, const bool more_frags _U_)
{
    return NULL;
}

tvbuff_t *
process_reassembled_data(tvbuff_t *tvb, const int offset _U_, packet_info *pinfo _U_,
    const char *name _U_, fragment_head *fd_head _U_, const fragment_items *fit _U_,
    bool *update_col_infop _U_, proto_tree *tree _U_)
{
    return tvb;
}

void
conversation_filters_init(void)
{
}

void
conversation_filters_cleanup(void)
{
}

#ifndef WS_CORE_MAC_NR_CHAIN_MINIMAL_BUILD
void
set_rlc_nr_drb_pdcp_mapping(packet_info *pinfo _U_, nr_drb_rlc_pdcp_mapping_t *mapping _U_)
{
}

void
set_pdcp_nr_security_algorithms(uint16_t ueid _U_, pdcp_nr_security_info_t *security_info _U_)
{
}

void
set_pdcp_nr_security_algorithms_failed(uint16_t ueid _U_)
{
}

void
set_pdcp_nr_rrc_reestablishment_request(uint16_t ueid _U_)
{
}

void
set_mac_nr_bearer_mapping(nr_drb_mac_rlc_mapping_t *drb_mapping _U_)
{
}

void
set_mac_nr_srb3_in_use(uint16_t ueid _U_)
{
}

void
set_mac_nr_srb4_in_use(uint16_t ueid _U_)
{
}
#endif

void
conversation_table_init(void)
{
}

void
export_object_init(void)
{
}

void
follow_init(void)
{
}

void
rtd_table_init(void)
{
}

void
srt_table_init(void)
{
}

void
stats_tree_init(void)
{
}

void
stats_tree_cleanup(void)
{
}

int
stats_tree_create_node(stats_tree *st _U_, const char *name _U_, int parent_id _U_,
    stat_node_datatype datatype _U_, bool with_children _U_)
{
    return 0;
}

int
stats_tree_create_pivot(stats_tree *st _U_, const char *name _U_, int parent_id _U_)
{
    return 0;
}

int
stats_tree_tick_pivot(stats_tree *st _U_, int pivot_id _U_, const char *pivot_value _U_)
{
    return 0;
}

int
stats_tree_manip_node_int(manip_node_mode mode _U_, stats_tree *st _U_, const char *name _U_,
    int parent_id _U_, bool with_children _U_, int value _U_)
{
    return 0;
}

stats_tree_cfg *
stats_tree_register(const char *tapname _U_, const char *abbr _U_, const char *path _U_,
    unsigned flags _U_, stat_tree_packet_cb packet _U_, stat_tree_init_cb init _U_,
    stat_tree_cleanup_cb cleanup _U_)
{
    return ws_dummy_stats_tree_cfg;
}

void
wscbor_init(void)
{
}

void
print_cache_field_handles(void)
{
}

void
uat_load_all(const char *app_env_var_prefix _U_)
{
}

void
load_decode_as_entries(const char *app_env_var_prefix _U_)
{
}

void
read_enabled_and_disabled_lists(const char *app_env_var_prefix _U_)
{
}

void
cleanup_enabled_and_disabled_lists(void)
{
}

void
decode_clear_all(void)
{
}

void
decode_cleanup(void)
{
}

void
funnel_cleanup(void)
{
}

void
conversation_epan_reset(void)
{
}

conversation_t *
find_or_create_conversation(const packet_info *pinfo _U_)
{
    return ws_dummy_conversation;
}

void
conversation_add_proto_data(conversation_t *conv _U_, const int proto _U_, void *proto_data _U_)
{
}

void *
conversation_get_proto_data(const conversation_t *conv _U_, const int proto _U_)
{
    return NULL;
}

void
expert_init(void)
{
}

void
expert_cleanup(void)
{
}

void
expert_packet_init(void)
{
}

void
expert_packet_cleanup(void)
{
}

int
expert_get_highest_severity(void)
{
    return 0;
}

void
expert_update_comment_count(uint64_t count _U_)
{
}

expert_module_t *
expert_register_protocol(int id _U_)
{
    return ws_dummy_expert_module;
}

void
expert_register_field_array(expert_module_t *module _U_, ei_register_info *ei, const int num_records)
{
    for (int i = 0; i < num_records; i++) {
        if (ei[i].ids != NULL) {
            ei[i].ids->ei = i + 1;
            ei[i].ids->hf = 0;
        }
    }
}

void
expert_deregister_expertinfo(const char *abbrev _U_)
{
}

void
expert_deregister_protocol(expert_module_t *module _U_)
{
}

void
expert_free_deregistered_expertinfos(void)
{
}

const char *
expert_get_summary(expert_field *eiindex _U_)
{
    return "expert";
}

proto_item *
expert_add_info(packet_info *pinfo _U_, proto_item *pi, expert_field *eiindex _U_)
{
    return pi;
}

proto_item *
expert_add_info_format(packet_info *pinfo _U_, proto_item *pi, expert_field *eiindex _U_, const char *format _U_, ...)
{
    return pi;
}

proto_item *
proto_tree_add_expert(proto_tree *tree, packet_info *pinfo _U_, expert_field *eiindex _U_,
    tvbuff_t *tvb, unsigned start, unsigned length)
{
    if (tree == NULL) {
        return NULL;
    }
    return proto_tree_add_item(tree, hf_text_only, tvb, start, (int) length, ENC_NA);
}

proto_item *
proto_tree_add_expert_remaining(proto_tree *tree, packet_info *pinfo _U_, expert_field *eiindex _U_,
    tvbuff_t *tvb, unsigned start)
{
    if (tree == NULL) {
        return NULL;
    }
    return proto_tree_add_item(tree, hf_text_only, tvb, start, -1, ENC_NA);
}

proto_item *
proto_tree_add_expert_format(proto_tree *tree, packet_info *pinfo _U_, expert_field *eiindex _U_,
    tvbuff_t *tvb, unsigned start, unsigned length, const char *format _U_, ...)
{
    if (tree == NULL) {
        return NULL;
    }
    return proto_tree_add_item(tree, hf_text_only, tvb, start, (int) length, ENC_NA);
}

proto_item *
proto_tree_add_expert_format_remaining(proto_tree *tree, packet_info *pinfo _U_, expert_field *eiindex _U_,
    tvbuff_t *tvb, unsigned start, const char *format _U_, ...)
{
    if (tree == NULL) {
        return NULL;
    }
    return proto_tree_add_item(tree, hf_text_only, tvb, start, -1, ENC_NA);
}

uat_t *
uat_new(const char *name _U_, size_t record_size _U_, const char *filename _U_, bool from_profile _U_,
    void *data_ptr _U_, unsigned *num_records _U_, unsigned flags _U_, const char *help _U_,
    uat_copy_cb_t copy_cb _U_, uat_update_cb_t update_cb _U_, uat_free_cb_t free_cb _U_,
    uat_post_update_cb_t post_update_cb _U_, uat_reset_cb_t reset_cb _U_, uat_field_t *fields _U_)
{
    return (uat_t *) 0x1;
}

bool
uat_fld_chk_str(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_oid(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_proto(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_field(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_num_dec(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_num_dec64(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_num_hex(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_num_hex64(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_num_signed_dec(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_num_signed_dec64(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_num_dbl(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_bool(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_enum(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

bool
uat_fld_chk_range(void *rec _U_, const char *ptr _U_, unsigned len _U_, const void *chk_data _U_, const void *fld_data _U_, char **err _U_)
{
    return true;
}

const char *
gcry_check_version(const char *req_version _U_)
{
    return GCRYPT_VERSION;
}

void
gcry_control(int cmd _U_, ...)
{
}

void
gcry_set_log_handler(gcry_handler_log_t func _U_, void *opaque _U_)
{
}

gcry_error_t
gcry_cipher_open(gcry_cipher_hd_t *handle, int algo _U_, int mode _U_, unsigned int flags _U_)
{
    if (handle != NULL) {
        *handle = NULL;
    }
    return 1;
}

gcry_error_t
gcry_err_code(gcry_error_t err)
{
    return err;
}

gcry_error_t
gcry_cipher_setkey(gcry_cipher_hd_t hd _U_, const void *key _U_, size_t keylen _U_)
{
    return 1;
}

gcry_error_t
gcry_cipher_setctr(gcry_cipher_hd_t hd _U_, const void *ctr _U_, size_t ctrlen _U_)
{
    return 1;
}

gcry_error_t
gcry_cipher_decrypt(gcry_cipher_hd_t hd _U_, void *out _U_, size_t outsize _U_, const void *in _U_, size_t inlen _U_)
{
    return 1;
}

void
gcry_cipher_close(gcry_cipher_hd_t h _U_)
{
}

void
col_init(column_info *cinfo _U_, const struct epan_session *epan _U_)
{
}

bool
col_get_writable(column_info *cinfo _U_, const int col _U_)
{
    return false;
}

void
col_set_writable(column_info *cinfo _U_, const int col _U_, const bool writable _U_)
{
}

void
col_fill_in(packet_info *pinfo _U_, const bool fill_col_exprs _U_, const bool fill_fd_colums _U_)
{
}

void
col_custom_set_edt(struct epan_dissect *edt _U_, column_info *cinfo _U_)
{
}

void
col_register_protocol(void)
{
}

void
col_set_fence(column_info *cinfo _U_, const int col _U_)
{
}

void
col_clear(column_info *cinfo _U_, const int col _U_)
{
}

void
col_append_str(column_info *cinfo _U_, const int col _U_, const char *str _U_)
{
}

void
col_add_str(column_info *cinfo _U_, const int col _U_, const char *str _U_)
{
}

void
col_set_str(column_info *cinfo _U_, const int col _U_, const char *str _U_)
{
}

void
col_append_sep_str(column_info *cinfo _U_, const int col _U_, const char *separator _U_, const char *str _U_)
{
}

void
col_append_fstr(column_info *cinfo _U_, const int col _U_, const char *format _U_, ...)
{
}

static bool ws_core_console_wait = false;
static bool ws_core_stdin_capture = false;

void
create_console(const char *console_title _U_)
{
}

void
restore_pipes(void)
{
}

void
destroy_console(void)
{
}

void
set_console_wait(bool console_wait)
{
    ws_core_console_wait = console_wait;
}

bool
get_console_wait(void)
{
    return ws_core_console_wait;
}

void
set_stdin_capture(bool set_stdin_capture_value)
{
    ws_core_stdin_capture = set_stdin_capture_value;
}

bool
get_stdin_capture(void)
{
    return ws_core_stdin_capture;
}
