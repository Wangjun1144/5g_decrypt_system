#include "config.h"
#include "wireshark.h"

#include "../../wireshark-bridge/include/ws_native_bridge.h"
#include "ws_core_json_export_minimal.h"

#include <glib.h>
#include <string.h>

#include <epan/address.h>
#include <epan/epan.h>
#include <epan/epan_dissect.h>
#include <epan/exceptions.h>
#include <epan/ftypes/ftypes.h>
#include <epan/packet.h>
#include <epan/proto.h>

#include <epan/dissectors/packet-mac-nr.h>

void proto_register_e212(void);
void proto_register_per(void);
void proto_register_ws_core_shared_fields(void);
void proto_register_ws_core_gsm_a_common_minimal(void);
void proto_register_nas_eps(void);
void proto_register_nas_5gs(void);
void proto_register_nr_rrc(void);
void proto_register_pdcp_nr(void);
void proto_register_rlc_nr(void);
void proto_register_mac_nr(void);
void proto_reg_handoff_nas_eps(void);
void proto_reg_handoff_nas_5gs(void);
void proto_reg_handoff_nr_rrc(void);
void proto_reg_handoff_pdcp_nr(void);
void proto_reg_handoff_rlc_nr(void);
void proto_reg_handoff_mac_nr(void);

static gboolean ws_core_initialized = FALSE;
static GMutex ws_core_init_mutex;
static epan_t *ws_core_session = NULL;

static void
ws_register_selected_protocols(register_cb cb _U_, void *user_data _U_)
{
    proto_register_e212();
    proto_register_per();
    proto_register_ws_core_shared_fields();
    proto_register_ws_core_gsm_a_common_minimal();
    proto_register_nas_eps();
    proto_register_nas_5gs();
    proto_register_nr_rrc();
    proto_register_pdcp_nr();
    proto_register_rlc_nr();
    proto_register_mac_nr();
}

static void
ws_register_selected_handoffs(register_cb cb _U_, void *user_data _U_)
{
    proto_reg_handoff_nas_eps();
    proto_reg_handoff_nas_5gs();
    proto_reg_handoff_nr_rrc();
    proto_reg_handoff_pdcp_nr();
    proto_reg_handoff_rlc_nr();
    proto_reg_handoff_mac_nr();
}

static char *
ws_native_strdup(const char *value)
{
    size_t length;
    char *copy;

    if (value == NULL) {
        return NULL;
    }

    length = strlen(value);
    copy = (char *) g_malloc(length + 1);
    memcpy(copy, value, length + 1);
    return copy;
}

static const char *
ws_proto_node_to_json_key(proto_node *node)
{
    if (node == NULL || node->finfo == NULL || node->finfo->hfinfo == NULL) {
        return "";
    }
    if (node->finfo->hfinfo->id != hf_text_only && node->finfo->hfinfo->abbrev != NULL) {
        return node->finfo->hfinfo->abbrev;
    }
    if (node->finfo->rep != NULL && node->finfo->rep->representation[0] != '\0') {
        return node->finfo->rep->representation;
    }
    return "";
}

static char *
ws_proto_node_value_repr(proto_node *node)
{
    field_info *fi;

    if (node == NULL) {
        return NULL;
    }

    fi = node->finfo;
    if (fi == NULL || fi->hfinfo == NULL || fi->value == NULL) {
        return NULL;
    }

    return fvalue_to_string_repr(NULL, fi->value, FTREPR_JSON, fi->hfinfo->display);
}

static void
ws_json_append_escaped(GString *out, const char *value)
{
    const unsigned char *p = (const unsigned char *) (value == NULL ? "" : value);
    while (*p != '\0') {
        switch (*p) {
            case '\\':
                g_string_append(out, "\\\\");
                break;
            case '"':
                g_string_append(out, "\\\"");
                break;
            case '\b':
                g_string_append(out, "\\b");
                break;
            case '\f':
                g_string_append(out, "\\f");
                break;
            case '\n':
                g_string_append(out, "\\n");
                break;
            case '\r':
                g_string_append(out, "\\r");
                break;
            case '\t':
                g_string_append(out, "\\t");
                break;
            default:
                if (*p < 0x20) {
                    g_string_append_printf(out, "\\u%04x", *p);
                } else {
                    g_string_append_c(out, (char) *p);
                }
                break;
        }
        p++;
    }
}

static gboolean
ws_core_ensure_initialized(char **error_utf8)
{
    static const struct packet_provider_funcs funcs = {
        NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
    };
    epan_app_data_t app_data;

    if (ws_core_initialized) {
        return TRUE;
    }

    g_mutex_lock(&ws_core_init_mutex);
    if (ws_core_initialized) {
        g_mutex_unlock(&ws_core_init_mutex);
        return TRUE;
    }

    memset(&app_data, 0, sizeof(app_data));
    app_data.env_var_prefix = "WIRESHARK";
    app_data.register_func = ws_register_selected_protocols;
    app_data.handoff_func = ws_register_selected_handoffs;

    if (!epan_init(NULL, NULL, false, &app_data)) {
        *error_utf8 = ws_native_strdup("epan_init failed for ws-core mac-nr bridge");
        g_mutex_unlock(&ws_core_init_mutex);
        return FALSE;
    }

    ws_core_session = epan_new(NULL, &funcs);
    if (ws_core_session == NULL) {
        *error_utf8 = ws_native_strdup("epan_new returned null");
        epan_cleanup();
        g_mutex_unlock(&ws_core_init_mutex);
        return FALSE;
    }

    ws_core_initialized = TRUE;
    g_mutex_unlock(&ws_core_init_mutex);
    return TRUE;
}

static gboolean
ws_collect_flat_fields(epan_dissect_t *edt, GString *json)
{
    GPtrArray *finfo_array;
    gboolean first = TRUE;

    finfo_array = proto_all_finfos(edt->tree);
    if (finfo_array == NULL) {
        return FALSE;
    }

    for (guint i = 0; i < finfo_array->len; i++) {
        field_info *fi = (field_info *) g_ptr_array_index(finfo_array, i);
        const header_field_info *hfinfo;
        char *repr;

        if (fi == NULL || fi->hfinfo == NULL || fi->value == NULL) {
            continue;
        }

        hfinfo = fi->hfinfo;
        if (hfinfo->abbrev == NULL || hfinfo->abbrev[0] == '\0') {
            continue;
        }

        repr = fvalue_to_string_repr(NULL, fi->value, FTREPR_DISPLAY, hfinfo->display);
        if (repr == NULL) {
            continue;
        }

        if (!first) {
            g_string_append_c(json, ',');
        }
        first = FALSE;

        g_string_append_c(json, '"');
        ws_json_append_escaped(json, hfinfo->abbrev);
        g_string_append(json, "\":\"");
        ws_json_append_escaped(json, repr);
        g_string_append_c(json, '"');

        wmem_free(NULL, repr);
    }

    return !first;
}

static void
ws_json_append_proto_node(GString *json, proto_node *node, gboolean include_offsets)
{
    proto_node *child;
    field_info *fi;
    char label[ITEM_LABEL_LENGTH];
    char display_label[ITEM_LABEL_LENGTH];
    char *value_repr;
    gboolean first_child = TRUE;

    if (node == NULL || node->finfo == NULL || node->finfo->hfinfo == NULL) {
        return;
    }

    fi = node->finfo;
    proto_item_fill_label(fi, label, NULL);
    proto_item_fill_display_label(fi, display_label, ITEM_LABEL_LENGTH);
    value_repr = ws_proto_node_value_repr(node);

    g_string_append_c(json, '{');
    g_string_append(json, "\"key\":\"");
    ws_json_append_escaped(json, ws_proto_node_to_json_key(node));
    g_string_append(json, "\",\"abbrev\":\"");
    ws_json_append_escaped(json, fi->hfinfo->abbrev == NULL ? "" : fi->hfinfo->abbrev);
    g_string_append(json, "\",\"name\":\"");
    ws_json_append_escaped(json, fi->hfinfo->name == NULL ? "" : fi->hfinfo->name);
    g_string_append(json, "\",\"label\":\"");
    ws_json_append_escaped(json, label);
    g_string_append(json, "\",\"display\":\"");
    ws_json_append_escaped(json, display_label);
    g_string_append(json, "\",\"value\":\"");
    ws_json_append_escaped(json, value_repr == NULL ? "" : value_repr);
    g_string_append(json, "\",\"children\":[");

    child = node->first_child;
    while (child != NULL) {
        if (!first_child) {
            g_string_append_c(json, ',');
        }
        first_child = FALSE;
        ws_json_append_proto_node(json, child, include_offsets);
        child = child->next;
    }
    g_string_append_c(json, ']');

    if (include_offsets) {
        g_string_append_printf(
                json,
                ",\"offset\":%d,\"length\":%d,\"bitmask\":%" G_GUINT64_FORMAT,
                fi->start,
                fi->length,
                fi->hfinfo->bitmask
        );
    }

    g_string_append_printf(
            json,
            ",\"hfType\":%d,\"treeType\":%d}",
            fi->hfinfo->type,
            fi->tree_type
    );

    if (value_repr != NULL) {
        wmem_free(NULL, value_repr);
    }
}

static gboolean
ws_append_field_tree(epan_dissect_t *edt, GString *json, gboolean include_offsets)
{
    proto_node *child;
    gboolean first = TRUE;

    if (edt == NULL || edt->tree == NULL) {
        return FALSE;
    }

    child = edt->tree->first_child;
    while (child != NULL) {
        if (!first) {
            g_string_append_c(json, ',');
        }
        first = FALSE;
        ws_json_append_proto_node(json, child, include_offsets);
        child = child->next;
    }

    return !first;
}

static gboolean
ws_append_grouped_tree(epan_dissect_t *edt, GString *json)
{
    if (edt == NULL) {
        return FALSE;
    }
    return ws_core_export_grouped_json_tree(edt->tree, json);
}

static guint
ws_count_proto_nodes_by_prefix(proto_node *node, const char *prefix)
{
    guint count = 0;
    proto_node *child;

    if (node == NULL || prefix == NULL) {
        return 0;
    }

    if (node->finfo != NULL && node->finfo->hfinfo != NULL && node->finfo->hfinfo->abbrev != NULL) {
        if (g_str_has_prefix(node->finfo->hfinfo->abbrev, prefix)) {
            count++;
        }
    }

    for (child = node->first_child; child != NULL; child = child->next) {
        count += ws_count_proto_nodes_by_prefix(child, prefix);
    }

    return count;
}

static const char *
ws_detect_rrc_message_name_from_node(proto_node *node)
{
    proto_node *child;
    const char *abbrev;

    if (node == NULL) {
        return NULL;
    }

    if (node->finfo != NULL && node->finfo->hfinfo != NULL) {
        abbrev = node->finfo->hfinfo->abbrev;
        if (abbrev != NULL) {
            if (strcmp(abbrev, "nr-rrc.ulInformationTransfer_element") == 0) {
                return "ULInformationTransfer";
            }
            if (strcmp(abbrev, "nr-rrc.rrcSetupComplete_element") == 0) {
                return "RRCSetupComplete";
            }
            if (strcmp(abbrev, "nr-rrc.rrcReconfigurationComplete_element") == 0) {
                return "RRCReconfigurationComplete";
            }
        }
    }

    for (child = node->first_child; child != NULL; child = child->next) {
        const char *nested = ws_detect_rrc_message_name_from_node(child);
        if (nested != NULL) {
            return nested;
        }
    }
    return NULL;
}

static gboolean
ws_decode_mac_nr_payload(const ws_native_mac_nr_decode_request *request, GString *json, char **error_utf8)
{
    epan_dissect_t *edt;
    dissector_handle_t handle;
    tvbuff_t *tvb;
    tvbuff_t *payload_tvb;
    frame_data fd;
    GString *fields_json;
    GString *tree_json;
    GString *grouped_tree_json;
    gboolean decoded = FALSE;
    gboolean have_fields = FALSE;
    gboolean have_tree = FALSE;
    gboolean have_grouped_tree = FALSE;
    guint nas_nodes = 0;
    guint rrc_nodes = 0;
    mac_nr_info *mac_info;
    unsigned offset = 0;
    const char *rrc_message_name = "";

    if (!ws_core_ensure_initialized(error_utf8)) {
        return FALSE;
    }

    handle = find_dissector("mac-nr");
    if (handle == NULL) {
        *error_utf8 = ws_native_strdup("mac-nr dissector handle not found");
        return FALSE;
    }

    memset(&fd, 0, sizeof(fd));
    fd.num = 1;
    fd.cap_len = (guint32) request->payload_length;
    fd.pkt_len = (guint32) request->payload_length;

    edt = epan_dissect_new(ws_core_session, TRUE, TRUE);
    if (edt == NULL) {
        *error_utf8 = ws_native_strdup("epan_dissect_new returned null");
        return FALSE;
    }

    edt->pi.epan = ws_core_session;
    edt->pi.current_proto = "mac-nr";
    edt->pi.fd = &fd;
    edt->pi.pseudo_header = NULL;
    edt->pi.noreassembly_reason = "";
    edt->pi.ptype = PT_NONE;
    edt->pi.use_conv_addr_port_endpoints = false;
    edt->pi.conv_addr_port_endpoints = NULL;
    edt->pi.conv_elements = NULL;
    edt->pi.p2p_dir = P2P_DIR_UNKNOWN;
    edt->pi.link_dir = LINK_DIR_UNKNOWN;
    edt->pi.layers = wmem_list_new(edt->pi.pool);
    clear_address(&edt->pi.dl_src);
    clear_address(&edt->pi.dl_dst);
    clear_address(&edt->pi.net_src);
    clear_address(&edt->pi.net_dst);
    clear_address(&edt->pi.src);
    clear_address(&edt->pi.dst);

    tvb = tvb_new_real_data(request->payload, (unsigned) request->payload_length, (unsigned) request->payload_length);
    edt->tvb = tvb;
    add_new_data_source(&edt->pi, tvb, "MAC-NR framed payload");

    mac_info = wmem_new0(wmem_file_scope(), mac_nr_info);
    offset = (unsigned) strlen(MAC_NR_START_STRING);

    TRY {
        if ((unsigned) request->payload_length <= offset ||
            memcmp(request->payload, MAC_NR_START_STRING, offset) != 0) {
            *error_utf8 = ws_native_strdup("payload does not start with mac-nr framing signature");
            decoded = FALSE;
        } else if (!dissect_mac_nr_context_fields(mac_info, tvb, &edt->pi, edt->tree, &offset)) {
            *error_utf8 = ws_native_strdup("failed to parse mac-nr context fields");
            decoded = FALSE;
        } else {
            set_mac_nr_proto_data(&edt->pi, mac_info);
            payload_tvb = tvb_new_subset_remaining(tvb, (int) offset);
            add_new_data_source(&edt->pi, payload_tvb, "MAC-NR");
            (void) call_dissector_only(handle, payload_tvb, &edt->pi, edt->tree, NULL);
            decoded = TRUE;
        }
    }
    CATCH_ALL {
        *error_utf8 = ws_native_strdup(GET_MESSAGE);
        decoded = FALSE;
    }
    ENDTRY;

    if (decoded) {
        fields_json = g_string_new(NULL);
        tree_json = g_string_new(NULL);
        grouped_tree_json = g_string_new(NULL);

        have_fields = ws_collect_flat_fields(edt, fields_json);
        have_tree = ws_append_field_tree(edt, tree_json, request->include_offsets != 0);
        have_grouped_tree = ws_append_grouped_tree(edt, grouped_tree_json);
        nas_nodes = ws_count_proto_nodes_by_prefix(edt->tree, "nas-5gs");
        rrc_nodes = ws_count_proto_nodes_by_prefix(edt->tree, "nr-rrc");
        rrc_message_name = ws_detect_rrc_message_name_from_node(edt->tree);
        if (rrc_message_name == NULL) {
            rrc_message_name = "";
        }

        g_string_append(json, "{\"bridgeVersion\":\"ws-core-minimal\",\"protocolName\":\"mac-nr-chain\",");
        g_string_append(json, "\"entryProtocol\":\"mac-nr\",\"rrcMessageName\":\"");
        ws_json_append_escaped(json, rrc_message_name);
        g_string_append(json, "\",\"_source\":{\"layers\":");
        if (have_grouped_tree) {
            g_string_append_len(json, grouped_tree_json->str, grouped_tree_json->len);
        } else {
            g_string_append(json, "{}");
        }
        g_string_append(json, "},\"legacy\":{\"flatFields\":{");
        if (have_fields) {
            g_string_append_len(json, fields_json->str, fields_json->len);
        }
        g_string_append(json, "},\"fieldTree\":[");
        if (have_tree) {
            g_string_append_len(json, tree_json->str, tree_json->len);
        }
        g_string_append(json, "],\"fieldTreeGrouped\":");
        if (have_grouped_tree) {
            g_string_append_len(json, grouped_tree_json->str, grouped_tree_json->len);
        } else {
            g_string_append(json, "{}");
        }
        g_string_append(json, "},\"diagnostics\":[");
        g_string_append(json, "{\"key\":\"nrRrcNodeCount\",\"value\":\"");
        g_string_append_printf(json, "%u", rrc_nodes);
        g_string_append(json, "\"},{\"key\":\"nasNodeCount\",\"value\":\"");
        g_string_append_printf(json, "%u", nas_nodes);
        g_string_append(json, "\"}]}");

        g_string_free(fields_json, TRUE);
        g_string_free(tree_json, TRUE);
        g_string_free(grouped_tree_json, TRUE);
    }

    epan_dissect_free(edt);
    return decoded;
}

int
ws_native_decode_mac_nr(const ws_native_mac_nr_decode_request *request, ws_native_decode_result *result)
{
    GString *json;
    char *error_utf8 = NULL;

    if (result == NULL) {
        return -1;
    }

    result->status_code = -1;
    result->json_utf8 = NULL;
    result->error_utf8 = NULL;

    if (request == NULL || request->payload == NULL || request->payload_length == 0) {
        result->error_utf8 = ws_native_strdup("payload must not be empty");
        return -1;
    }

    json = g_string_new(NULL);
    if (!ws_decode_mac_nr_payload(request, json, &error_utf8)) {
        g_string_free(json, TRUE);
        result->error_utf8 = error_utf8 == NULL ? ws_native_strdup("mac-nr chain decode failed") : error_utf8;
        return -1;
    }

    result->status_code = 0;
    result->json_utf8 = g_string_free(json, FALSE);
    return 0;
}

void
ws_native_free_result(ws_native_decode_result *result)
{
    if (result == NULL) {
        return;
    }

    g_free(result->json_utf8);
    g_free(result->error_utf8);
    result->json_utf8 = NULL;
    result->error_utf8 = NULL;
    result->status_code = 0;
}
