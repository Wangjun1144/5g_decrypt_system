#include "config.h"
#include "wireshark.h"

#include "../../wireshark-bridge/include/ws_native_bridge.h"
#include "ws_core_json_export_minimal.h"

#include <glib.h>
#include <string.h>

#include <epan/epan.h>
#include <epan/epan_dissect.h>
#include <epan/packet.h>
#include <epan/proto.h>
#include <epan/address.h>
#include <epan/exceptions.h>
#include <epan/ftypes/ftypes.h>

void proto_register_e212(void);
void proto_register_ws_core_shared_fields(void);
void proto_register_ws_core_gsm_a_common_minimal(void);
void proto_register_nas_eps(void);
void proto_register_nas_5gs(void);
void proto_reg_handoff_nas_eps(void);
void proto_reg_handoff_nas_5gs(void);

static gboolean ws_core_initialized = FALSE;
static GMutex ws_core_init_mutex;
static epan_t *ws_core_session = NULL;

static void
ws_register_selected_protocols(register_cb cb _U_, void *user_data _U_)
{
    proto_register_e212();
    proto_register_ws_core_shared_fields();
    proto_register_ws_core_gsm_a_common_minimal();
    proto_register_nas_eps();
    proto_register_nas_5gs();
}

static void
ws_register_selected_handoffs(register_cb cb _U_, void *user_data _U_)
{
    proto_reg_handoff_nas_eps();
    proto_reg_handoff_nas_5gs();
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

static const char *
ws_native_message_name(int message_type)
{
    switch (message_type) {
        case 0x41:
            return "Registration request";
        case 0x56:
            return "Authentication request";
        case 0x57:
            return "Authentication response";
        case 0x5d:
            return "Security mode command";
        case 0x5e:
            return "Security mode complete";
        case 0x5c:
            return "Identity response";
        case 0xd6:
            return "5GSM status";
        case 0xc1:
            return "PDU session establishment request";
        case 0xc2:
            return "PDU session establishment accept";
        case 0xc3:
            return "PDU session establishment reject";
        default:
            return "decoded";
    }
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

static gboolean
ws_proto_node_has_value(proto_node *node)
{
    char *value_repr;
    gboolean has_value;

    value_repr = ws_proto_node_value_repr(node);
    has_value = value_repr != NULL;
    if (value_repr != NULL) {
        wmem_free(NULL, value_repr);
    }
    return has_value;
}

static gboolean
ws_any_group_node_has_children(GSList *node_values_list)
{
    GSList *current = node_values_list;

    while (current != NULL) {
        proto_node *node = (proto_node *) current->data;
        if (node != NULL && node->first_child != NULL) {
            return TRUE;
        }
        current = current->next;
    }
    return FALSE;
}

static GSList *
ws_proto_node_group_children_by_json_key(proto_node *node)
{
    GSList *same_key_nodes_list = NULL;
    GHashTable *lookup_by_json_key;
    proto_node *current_child;

    if (node == NULL) {
        return NULL;
    }

    lookup_by_json_key = g_hash_table_new(g_str_hash, g_str_equal);
    current_child = node->first_child;

    while (current_child != NULL) {
        const char *json_key = ws_proto_node_to_json_key(current_child);
        GSList *json_key_nodes = (GSList *) g_hash_table_lookup(lookup_by_json_key, json_key);

        if (json_key_nodes == NULL) {
            json_key_nodes = g_slist_append(NULL, current_child);
            same_key_nodes_list = g_slist_prepend(same_key_nodes_list, json_key_nodes);
        } else {
            json_key_nodes = g_slist_append(json_key_nodes, current_child);
        }

        g_hash_table_insert(lookup_by_json_key, (gpointer) json_key, json_key_nodes);
        current_child = current_child->next;
    }

    g_hash_table_destroy(lookup_by_json_key);
    return g_slist_reverse(same_key_nodes_list);
}

static void
ws_json_append_grouped_node_value(proto_node *node, GString *json)
{
    field_info *fi;
    char label[ITEM_LABEL_LENGTH];
    char *value_repr;

    if (node == NULL || node->finfo == NULL || node->finfo->hfinfo == NULL) {
        g_string_append(json, "\"\"");
        return;
    }

    fi = node->finfo;
    value_repr = ws_proto_node_value_repr(node);
    if (value_repr != NULL) {
        g_string_append_c(json, '"');
        ws_json_append_escaped(json, value_repr);
        g_string_append_c(json, '"');
        wmem_free(NULL, value_repr);
        return;
    }

    if (fi->hfinfo->type == FT_PROTOCOL) {
        if (fi->rep != NULL && fi->rep->representation[0] != '\0') {
            g_string_append_c(json, '"');
            ws_json_append_escaped(json, fi->rep->representation);
            g_string_append_c(json, '"');
            return;
        }

        proto_item_fill_label(fi, label, NULL);
        g_string_append_c(json, '"');
        ws_json_append_escaped(json, label);
        g_string_append_c(json, '"');
        return;
    }

    g_string_append(json, "\"\"");
}

static void ws_json_append_grouped_child_object(proto_node *node, GString *json);

static void
ws_json_append_grouped_value_list(GSList *node_values_head, GString *json)
{
    GSList *current = node_values_head;

    if (current == NULL) {
        g_string_append(json, "\"\"");
        return;
    }

    if (current->next == NULL) {
        ws_json_append_grouped_node_value((proto_node *) current->data, json);
        return;
    }

    g_string_append_c(json, '[');
    while (current != NULL) {
        if (current != node_values_head) {
            g_string_append_c(json, ',');
        }
        ws_json_append_grouped_node_value((proto_node *) current->data, json);
        current = current->next;
    }
    g_string_append_c(json, ']');
}

static void
ws_json_append_grouped_child_object(proto_node *node, GString *json)
{
    GSList *grouped_children_list;
    GSList *current_group;
    gboolean first_group = TRUE;

    if (node == NULL || node->first_child == NULL) {
        g_string_append(json, "{}");
        return;
    }

    grouped_children_list = ws_proto_node_group_children_by_json_key(node);
    g_string_append_c(json, '{');

    for (current_group = grouped_children_list; current_group != NULL; current_group = current_group->next) {
        GSList *node_values_list = (GSList *) current_group->data;
        proto_node *first_value;
        const char *json_key;
        gboolean has_value;
        gboolean has_children;

        if (node_values_list == NULL) {
            continue;
        }

        first_value = (proto_node *) node_values_list->data;
        json_key = ws_proto_node_to_json_key(first_value);
        has_value = ws_proto_node_has_value(first_value);
        has_children = ws_any_group_node_has_children(node_values_list);

        if (has_value) {
            if (!first_group) {
                g_string_append_c(json, ',');
            }
            first_group = FALSE;
            g_string_append_c(json, '"');
            ws_json_append_escaped(json, json_key);
            g_string_append(json, "\":");
            ws_json_append_grouped_value_list(node_values_list, json);
        }

        if (has_children) {
            const char *suffix = has_value ? "_tree" : "";
            GSList *current_value;

            if (!first_group) {
                g_string_append_c(json, ',');
            }
            first_group = FALSE;
            g_string_append_c(json, '"');
            ws_json_append_escaped(json, json_key);
            ws_json_append_escaped(json, suffix);
            g_string_append(json, "\":");

            if (node_values_list->next == NULL) {
                proto_node *only_value = (proto_node *) node_values_list->data;
                if (only_value->first_child != NULL) {
                    ws_json_append_grouped_child_object(only_value, json);
                } else {
                    ws_json_append_grouped_node_value(only_value, json);
                }
            } else {
                g_string_append_c(json, '[');
                for (current_value = node_values_list; current_value != NULL; current_value = current_value->next) {
                    proto_node *value_node = (proto_node *) current_value->data;
                    if (current_value != node_values_list) {
                        g_string_append_c(json, ',');
                    }
                    if (value_node->first_child != NULL) {
                        ws_json_append_grouped_child_object(value_node, json);
                    } else {
                        ws_json_append_grouped_node_value(value_node, json);
                    }
                }
                g_string_append_c(json, ']');
            }
        }

        if (!has_value && !has_children) {
            if (!first_group) {
                g_string_append_c(json, ',');
            }
            first_group = FALSE;
            g_string_append_c(json, '"');
            ws_json_append_escaped(json, json_key);
            g_string_append(json, "\":");
            ws_json_append_grouped_value_list(node_values_list, json);
        }
    }

    g_string_append_c(json, '}');
    g_slist_free_full(grouped_children_list, (GDestroyNotify) g_slist_free);
}

static gboolean
ws_append_grouped_tree(epan_dissect_t *edt, GString *json)
{
    if (edt == NULL) {
        return FALSE;
    }

    return ws_core_export_grouped_json_tree(edt->tree, json);
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
        *error_utf8 = ws_native_strdup("epan_init failed for ws-core nas bridge");
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
ws_collect_flat_fields(epan_dissect_t *edt, GString *json, int *message_type_out)
{
    GPtrArray *finfo_array;
    gboolean first = TRUE;

    *message_type_out = -1;
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

        if (*message_type_out < 0 && strcmp(hfinfo->abbrev, "nas-5gs.mm.message_type") == 0) {
            *message_type_out = (int) g_ascii_strtoll(repr, NULL, 0);
        }

        wmem_free(NULL, repr);
    }

    return !first;
}

static gboolean
ws_decode_nas_payload(const ws_native_nas_decode_request *request, GString *json, char **error_utf8)
{
    epan_dissect_t *edt;
    dissector_handle_t handle;
    tvbuff_t *tvb;
    frame_data fd;
    GString *fields_json;
    GString *tree_json;
    GString *grouped_tree_json;
    gboolean decoded = FALSE;
    int message_type = -1;
    gboolean have_fields = FALSE;
    gboolean have_tree = FALSE;
    gboolean have_grouped_tree = FALSE;

    if (!ws_core_ensure_initialized(error_utf8)) {
        return FALSE;
    }

    handle = find_dissector("nas-5gs");
    if (handle == NULL) {
        *error_utf8 = ws_native_strdup("nas-5gs dissector handle not found");
        return FALSE;
    }

    memset(&fd, 0, sizeof(fd));
    fd.num = 1;
    fd.cap_len = (guint32) request->payload_length;
    fd.pkt_len = (guint32) request->payload_length;

    edt = epan_dissect_new(ws_core_session, true, true);
    if (edt == NULL) {
        *error_utf8 = ws_native_strdup("epan_dissect_new returned null");
        return FALSE;
    }

    edt->pi.epan = ws_core_session;
    edt->pi.current_proto = "nas-5gs";
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
    add_new_data_source(&edt->pi, tvb, "NAS-5GS");

    TRY {
        (void) call_dissector_only(handle, tvb, &edt->pi, edt->tree, NULL);
        decoded = TRUE;
    }
    CATCH_ALL {
        *error_utf8 = ws_native_strdup(GET_MESSAGE);
        decoded = FALSE;
    }
    ENDTRY;

    if (decoded) {
        fields_json = g_string_new(NULL);
        have_fields = ws_collect_flat_fields(edt, fields_json, &message_type);
        tree_json = g_string_new(NULL);
        have_tree = ws_append_field_tree(edt, tree_json, request->include_offsets != 0);
        grouped_tree_json = g_string_new(NULL);
        have_grouped_tree = ws_append_grouped_tree(edt, grouped_tree_json);
        g_string_append(json, "{\"bridgeVersion\":\"ws-core-minimal\",\"protocolName\":\"nas-5gs\",");
        g_string_append_printf(json, "\"messageType\":%d,", message_type);
        g_string_append(json, "\"messageTypeName\":\"");
        ws_json_append_escaped(json, ws_native_message_name(message_type));
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
        g_string_append(json, "},\"diagnostics\":[]}");
        g_string_free(fields_json, TRUE);
        g_string_free(tree_json, TRUE);
        g_string_free(grouped_tree_json, TRUE);
    }

    epan_dissect_free(edt);
    return decoded;
}

int
ws_native_decode_nas_5gs(const ws_native_nas_decode_request *request, ws_native_decode_result *result)
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
    if (!ws_decode_nas_payload(request, json, &error_utf8)) {
        g_string_free(json, TRUE);
        result->error_utf8 = error_utf8 == NULL ? ws_native_strdup("nas decode failed") : error_utf8;
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
