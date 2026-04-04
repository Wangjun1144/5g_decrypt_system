#include "config.h"
#include "wireshark.h"

#include "ws_core_json_export_minimal.h"

#include <epan/proto.h>
#include <epan/packet.h>
#include <wsutil/json_dumper.h>

/*
 * Minimal JSON tree exporter directly adapted from Wireshark epan/print.c.
 * This intentionally keeps Wireshark's grouping and "_tree" behavior so the
 * DLL output converges toward tshark/Wireshark JSON semantics.
 */

typedef GSList* (*proto_node_children_grouper_func)(proto_node *node);

typedef struct {
    proto_node_children_grouper_func node_children_grouper;
    json_dumper *dumper;
} ws_core_write_json_data;

typedef void (*ws_core_proto_node_value_writer)(proto_node *, ws_core_write_json_data *);

static void ws_core_write_json_proto_node_list(GSList *proto_node_list_head, ws_core_write_json_data *data);
static void ws_core_write_json_proto_node(
        GSList *node_values_head,
        const char *suffix,
        ws_core_proto_node_value_writer value_writer,
        ws_core_write_json_data *data
);
static void ws_core_write_json_proto_node_value_list(
        GSList *node_values_head,
        ws_core_proto_node_value_writer value_writer,
        ws_core_write_json_data *data
);
static void ws_core_write_json_proto_node_dynamic(proto_node *node, ws_core_write_json_data *data);
static void ws_core_write_json_proto_node_children(proto_node *node, ws_core_write_json_data *data);
static void ws_core_write_json_proto_node_value(proto_node *node, ws_core_write_json_data *data);
static void ws_core_write_json_proto_node_no_value(proto_node *node, ws_core_write_json_data *data);
static const char *ws_core_proto_node_to_json_key(proto_node *node);
static GSList *ws_core_proto_node_group_children_by_json_key(proto_node *node);
static void ws_core_trim_trailing_json_whitespace(GString *json);

static bool
ws_core_any_has_children(GSList *node_values_list)
{
    GSList *current_node = node_values_list;
    while (current_node != NULL) {
        proto_node *current_value = (proto_node *) current_node->data;
        if (current_value->first_child != NULL) {
            return true;
        }
        current_node = current_node->next;
    }
    return false;
}

static void
ws_core_write_json_proto_node_list(GSList *proto_node_list_head, ws_core_write_json_data *pdata)
{
    GSList *current_node = proto_node_list_head;

    json_dumper_begin_object(pdata->dumper);

    while (current_node != NULL) {
        GSList *node_values_list = (GSList *) current_node->data;
        proto_node *first_value = (proto_node *) node_values_list->data;
        const char *json_key = ws_core_proto_node_to_json_key(first_value);
        field_info *fi = first_value->finfo;
        char *value_string_repr = fvalue_to_string_repr(NULL, fi->value, FTREPR_JSON, fi->hfinfo->display);
        bool has_children = ws_core_any_has_children(node_values_list);
        bool has_value = value_string_repr != NULL;

        wmem_free(NULL, value_string_repr);

        if (has_value) {
            ws_core_write_json_proto_node(node_values_list, "", ws_core_write_json_proto_node_value, pdata);
        }

        if (has_children) {
            const char *suffix = has_value ? "_tree" : "";
            ws_core_write_json_proto_node(node_values_list, suffix, ws_core_write_json_proto_node_dynamic, pdata);
        }

        if (!has_value && !has_children) {
            ws_core_write_json_proto_node(node_values_list, "", ws_core_write_json_proto_node_no_value, pdata);
        }

        current_node = current_node->next;
    }

    json_dumper_end_object(pdata->dumper);
}

static void
ws_core_write_json_proto_node(
        GSList *node_values_head,
        const char *suffix,
        ws_core_proto_node_value_writer value_writer,
        ws_core_write_json_data *pdata)
{
    proto_node *first_value = (proto_node *) node_values_head->data;
    const char *json_key = ws_core_proto_node_to_json_key(first_value);
    char *json_key_suffix = g_strdup_printf("%s%s", json_key, suffix);

    json_dumper_set_member_name(pdata->dumper, json_key_suffix);
    g_free(json_key_suffix);
    ws_core_write_json_proto_node_value_list(node_values_head, value_writer, pdata);
}

static void
ws_core_write_json_proto_node_value_list(
        GSList *node_values_head,
        ws_core_proto_node_value_writer value_writer,
        ws_core_write_json_data *pdata)
{
    GSList *current_value = node_values_head;

    if (current_value->next == NULL) {
        value_writer((proto_node *) current_value->data, pdata);
    } else {
        json_dumper_begin_array(pdata->dumper);
        while (current_value != NULL) {
            value_writer((proto_node *) current_value->data, pdata);
            current_value = current_value->next;
        }
        json_dumper_end_array(pdata->dumper);
    }
}

static void
ws_core_write_json_proto_node_dynamic(proto_node *node, ws_core_write_json_data *data)
{
    if (node->first_child == NULL) {
        ws_core_write_json_proto_node_no_value(node, data);
    } else {
        ws_core_write_json_proto_node_children(node, data);
    }
}

static void
ws_core_write_json_proto_node_children(proto_node *node, ws_core_write_json_data *data)
{
    GSList *grouped_children_list = data->node_children_grouper(node);
    ws_core_write_json_proto_node_list(grouped_children_list, data);
    g_slist_free_full(grouped_children_list, (GDestroyNotify) g_slist_free);
}

static void
ws_core_write_json_proto_node_value(proto_node *node, ws_core_write_json_data *pdata)
{
    field_info *fi = node->finfo;
    char *value_string_repr = fvalue_to_string_repr(NULL, fi->value, FTREPR_JSON, fi->hfinfo->display);

    json_dumper_value_string(pdata->dumper, value_string_repr);
    wmem_free(NULL, value_string_repr);
}

static void
ws_core_write_json_proto_node_no_value(proto_node *node, ws_core_write_json_data *pdata)
{
    field_info *fi = node->finfo;

    if (fi->hfinfo->type == FT_PROTOCOL) {
        if (fi->rep) {
            json_dumper_value_string(pdata->dumper, fi->rep->representation);
        } else {
            char label_str[ITEM_LABEL_LENGTH];
            proto_item_fill_label(fi, label_str, NULL);
            json_dumper_value_string(pdata->dumper, label_str);
        }
    } else {
        json_dumper_value_string(pdata->dumper, "");
    }
}

static const char *
ws_core_proto_node_to_json_key(proto_node *node)
{
    const char *json_key;

    if (node->finfo->hfinfo->id != hf_text_only) {
        json_key = node->finfo->hfinfo->abbrev;
    } else if (node->finfo->rep != NULL) {
        json_key = node->finfo->rep->representation;
    } else {
        json_key = "";
    }

    return json_key;
}

static GSList *
ws_core_proto_node_group_children_by_json_key(proto_node *node)
{
    GSList *same_key_nodes_list = NULL;
    GHashTable *lookup_by_json_key = g_hash_table_new(g_str_hash, g_str_equal);
    proto_node *current_child = node->first_child;

    while (current_child != NULL) {
        char *json_key = (char *) ws_core_proto_node_to_json_key(current_child);
        GSList *json_key_nodes = (GSList *) g_hash_table_lookup(lookup_by_json_key, json_key);

        if (json_key_nodes == NULL) {
            json_key_nodes = g_slist_append(json_key_nodes, current_child);
            same_key_nodes_list = g_slist_prepend(same_key_nodes_list, json_key_nodes);
            g_hash_table_insert(lookup_by_json_key, json_key, json_key_nodes);
        } else {
            json_key_nodes = g_slist_append(json_key_nodes, current_child);
            g_hash_table_insert(lookup_by_json_key, json_key, json_key_nodes);
        }

        current_child = current_child->next;
    }

    g_hash_table_destroy(lookup_by_json_key);
    return g_slist_reverse(same_key_nodes_list);
}

gboolean
ws_core_export_grouped_json_tree(proto_node *root, GString *json)
{
    json_dumper dumper = {0};
    ws_core_write_json_data data;
    gboolean finished;

    if (root == NULL || root->first_child == NULL || json == NULL) {
        return FALSE;
    }

    dumper.output_string = json;
    data.node_children_grouper = ws_core_proto_node_group_children_by_json_key;
    data.dumper = &dumper;

    ws_core_write_json_proto_node_children(root, &data);
    finished = json_dumper_finish(&dumper);
    if (finished) {
        ws_core_trim_trailing_json_whitespace(json);
    }
    return finished;
}

static void
ws_core_trim_trailing_json_whitespace(GString *json)
{
    while (json->len > 0) {
        char ch = json->str[json->len - 1];
        if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') {
            break;
        }
        g_string_truncate(json, json->len - 1);
    }
}
