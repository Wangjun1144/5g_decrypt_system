#include "config.h"
#include "wireshark.h"

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

#include <epan/epan.h>
#include <epan/epan_dissect.h>
#include <epan/packet.h>
#include <epan/proto.h>
#include <epan/expert.h>
#include <epan/tap.h>
#include <epan/wmem_scopes.h>
#include <epan/dfilter/dfilter.h>
#include <epan/except.h>
#include <epan/conversation.h>

#include <wsutil/value_string.h>

#include <glib.h>
#include <string.h>

static wmem_allocator_t *ws_pinfo_pool_cache = NULL;
static char *ws_epan_env_prefix = NULL;
static int ws_always_visible_refcount = 0;

static void
ws_register_minimal_dissector_tables(void)
{
    if (find_heur_dissector_list("udp") == NULL) {
        register_heur_dissector_list("udp", -1);
    }

    if (find_dissector_table("tcp.port") == NULL) {
        dissector_table_t tcp_port_table;

        tcp_port_table = register_dissector_table("tcp.port", "TCP Port", -1, FT_UINT16, BASE_DEC);
        dissector_table_allow_decode_as(tcp_port_table);
    }

    if (find_dissector_table("media_type") == NULL) {
        register_dissector_table("media_type", "Media Type", -1, FT_STRING, STRING_CASE_INSENSITIVE);
    }
}

struct epan_session {
    struct packet_provider_data *prov;
    struct packet_provider_funcs funcs;
};

bool wireshark_abort_on_dissector_bug = false;
bool wireshark_abort_on_too_many_items = false;

void
ws_dissector_bug(const char *format, ...)
{
    va_list ap;

    va_start(ap, format);
    vfprintf(stderr, format, ap);
    va_end(ap);

    if (wireshark_abort_on_dissector_bug) {
        abort();
    }
}

const char *
epan_get_version(void)
{
    return VERSION;
}

void
epan_get_version_number(int *major, int *minor, int *micro)
{
    if (major != NULL) {
        *major = VERSION_MAJOR;
    }
    if (minor != NULL) {
        *minor = VERSION_MINOR;
    }
    if (micro != NULL) {
        *micro = VERSION_MICRO;
    }
}

const char *
epan_get_environment_prefix(void)
{
    return ws_epan_env_prefix;
}

void
epan_register_plugin(const epan_plugin *plugin _U_)
{
}

int
epan_plugins_supported(void)
{
    return -1;
}

bool
epan_init(register_cb cb, void *client_data, bool load_plugins _U_, epan_app_data_t *app_data)
{
    wmem_init_scopes();
    value_string_externals_init();
    except_init();

    g_free(ws_epan_env_prefix);
    ws_epan_env_prefix = g_strdup((app_data != NULL && app_data->env_var_prefix != NULL) ? app_data->env_var_prefix : "WIRESHARK");

    memset(&prefs, 0, sizeof(prefs));
    prefs.gui_max_tree_items = 1000000;
    prefs.gui_max_tree_depth = 256;

    tap_init();
    proto_pre_init();
    prefs_init((app_data != NULL) ? app_data->col_fmt : NULL, (app_data != NULL) ? app_data->num_cols : 0);
    expert_init();
    packet_init();
    ws_register_minimal_dissector_tables();

    proto_init(NULL, NULL,
        (app_data != NULL) ? app_data->register_func : NULL,
        (app_data != NULL) ? app_data->handoff_func : NULL,
        cb, client_data);
    final_registration_all_protocols();

    return true;
}

e_prefs *
epan_load_settings(void)
{
    return &prefs;
}

void
epan_cleanup(void)
{
    packet_cleanup();
    prefs_cleanup();
    proto_cleanup();
    tap_cleanup();
    expert_cleanup();

    if (ws_pinfo_pool_cache != NULL) {
        wmem_destroy_allocator(ws_pinfo_pool_cache);
        ws_pinfo_pool_cache = NULL;
    }

    value_string_externals_cleanup();
    wmem_cleanup_scopes();

    g_free(ws_epan_env_prefix);
    ws_epan_env_prefix = NULL;
}

epan_t *
epan_new(struct packet_provider_data *prov, const struct packet_provider_funcs *funcs)
{
    epan_t *session = g_slice_new0(epan_t);

    session->prov = prov;
    if (funcs != NULL) {
        session->funcs = *funcs;
    }

    init_dissection(ws_epan_env_prefix);
    return session;
}

wtap_block_t
epan_get_modified_block(const epan_t *session, const frame_data *fd)
{
    if (session != NULL && session->funcs.get_modified_block != NULL) {
        return session->funcs.get_modified_block(session->prov, fd);
    }
    return NULL;
}

const char *
epan_get_interface_name(const epan_t *session, uint32_t interface_id, unsigned section_number)
{
    if (session != NULL && session->funcs.get_interface_name != NULL) {
        return session->funcs.get_interface_name(session->prov, interface_id, section_number);
    }
    return NULL;
}

const char *
epan_get_interface_description(const epan_t *session, uint32_t interface_id, unsigned section_number)
{
    if (session != NULL && session->funcs.get_interface_description != NULL) {
        return session->funcs.get_interface_description(session->prov, interface_id, section_number);
    }
    return NULL;
}

int32_t
epan_get_process_id(const epan_t *session, uint32_t process_info_id, unsigned section_number)
{
    if (session != NULL && session->funcs.get_process_id != NULL) {
        return session->funcs.get_process_id(session->prov, process_info_id, section_number);
    }
    return -1;
}

const char *
epan_get_process_name(const epan_t *session, uint32_t process_info_id, unsigned section_number)
{
    if (session != NULL && session->funcs.get_process_name != NULL) {
        return session->funcs.get_process_name(session->prov, process_info_id, section_number);
    }
    return NULL;
}

const uint8_t *
epan_get_process_uuid(const epan_t *session, uint32_t process_info_id, unsigned section_number, size_t *uuid_size)
{
    if (session != NULL && session->funcs.get_process_uuid != NULL) {
        return session->funcs.get_process_uuid(session->prov, process_info_id, section_number, uuid_size);
    }
    return NULL;
}

const nstime_t *
epan_get_frame_ts(const epan_t *session, uint32_t frame_num)
{
    if (session != NULL && session->funcs.get_frame_ts != NULL) {
        return session->funcs.get_frame_ts(session->prov, frame_num);
    }
    return NULL;
}

const nstime_t *
epan_get_start_ts(const epan_t *session)
{
    if (session != NULL && session->funcs.get_start_ts != NULL) {
        return session->funcs.get_start_ts(session->prov);
    }
    return NULL;
}

void
epan_free(epan_t *session)
{
    if (session != NULL) {
        cleanup_dissection();
        g_slice_free(epan_t, session);
    }
}

void
epan_conversation_init(void)
{
    conversation_epan_reset();
}

void
epan_set_always_visible(bool force)
{
    if (force) {
        ws_always_visible_refcount++;
    } else if (ws_always_visible_refcount > 0) {
        ws_always_visible_refcount--;
    }
}

void
epan_dissect_init(epan_dissect_t *edt, epan_t *session, const bool create_proto_tree, const bool proto_tree_visible)
{
    ws_assert(edt != NULL);

    edt->session = session;
    memset(&edt->pi, 0, sizeof(edt->pi));

    if (ws_pinfo_pool_cache != NULL) {
        edt->pi.pool = ws_pinfo_pool_cache;
        ws_pinfo_pool_cache = NULL;
    } else {
        edt->pi.pool = wmem_allocator_new(WMEM_ALLOCATOR_BLOCK_FAST);
    }

    if (create_proto_tree) {
        edt->tree = proto_tree_create_root(&edt->pi);
        proto_tree_set_visible(edt->tree, (ws_always_visible_refcount > 0) ? true : proto_tree_visible);
    } else {
        edt->tree = NULL;
    }

    edt->tvb = NULL;
}

epan_dissect_t *
epan_dissect_new(epan_t *session, const bool create_proto_tree, const bool proto_tree_visible)
{
    epan_dissect_t *edt = g_new0(epan_dissect_t, 1);
    epan_dissect_init(edt, session, create_proto_tree, proto_tree_visible);
    return edt;
}

void
epan_dissect_reset(epan_dissect_t *edt)
{
    wmem_allocator_t *tmp;

    ws_assert(edt != NULL);

    g_slist_free(edt->pi.proto_data);
    free_data_sources(&edt->pi);

    if (edt->tvb != NULL) {
        tvb_free_chain(edt->tvb);
        edt->tvb = NULL;
    }

    if (edt->tree != NULL) {
        proto_tree_reset(edt->tree);
    }

    tmp = edt->pi.pool;
    wmem_free_all(tmp);
    memset(&edt->pi, 0, sizeof(edt->pi));
    edt->pi.pool = tmp;
}

void
epan_dissect_fake_protocols(epan_dissect_t *edt, const bool fake_protocols)
{
    if (edt != NULL) {
        proto_tree_set_fake_protocols(edt->tree, fake_protocols);
    }
}

void
epan_dissect_run(epan_dissect_t *edt, int file_type_subtype, wtap_rec *rec, frame_data *fd, struct epan_column_info *cinfo)
{
    dissect_record(edt, file_type_subtype, rec, fd, cinfo);
}

void
epan_dissect_run_with_taps(epan_dissect_t *edt, int file_type_subtype, wtap_rec *rec, frame_data *fd, struct epan_column_info *cinfo)
{
    tap_queue_init(edt);
    dissect_record(edt, file_type_subtype, rec, fd, cinfo);
    tap_push_tapped_queue(edt);
}

void
epan_dissect_file_run(epan_dissect_t *edt, wtap_rec *rec, frame_data *fd, struct epan_column_info *cinfo)
{
    dissect_file(edt, rec, fd, cinfo);
}

void
epan_dissect_file_run_with_taps(epan_dissect_t *edt, wtap_rec *rec, frame_data *fd, struct epan_column_info *cinfo)
{
    tap_queue_init(edt);
    dissect_file(edt, rec, fd, cinfo);
    tap_push_tapped_queue(edt);
}

void
epan_dissect_prime_with_dfilter(epan_dissect_t *edt, const struct epan_dfilter *dfcode)
{
    dfilter_prime_proto_tree((const dfilter_t *) dfcode, edt->tree);
}

void
epan_dissect_prime_with_dfilter_print(epan_dissect_t *edt, const struct epan_dfilter *dfcode)
{
    dfilter_prime_proto_tree_print((const dfilter_t *) dfcode, edt->tree);
}

void
epan_dissect_prime_with_hfid(epan_dissect_t *edt, int hfid)
{
    proto_tree_prime_with_hfid(edt->tree, hfid);
}

void
epan_dissect_prime_with_hfid_array(epan_dissect_t *edt, GArray *hfids)
{
    for (guint i = 0; i < hfids->len; i++) {
        proto_tree_prime_with_hfid(edt->tree, g_array_index(hfids, int, i));
    }
}

bool
epan_dissect_packet_contains_field(epan_dissect_t *edt, const char *field_name)
{
    GPtrArray *array;
    int field_id;
    bool contains_field;

    if (edt == NULL || edt->tree == NULL) {
        return false;
    }

    field_id = proto_get_id_by_filter_name(field_name);
    if (field_id < 0) {
        return false;
    }

    array = proto_find_finfo(edt->tree, field_id);
    contains_field = (array->len > 0);
    g_ptr_array_free(array, true);
    return contains_field;
}

void
epan_dissect_cleanup(epan_dissect_t *edt)
{
    ws_assert(edt != NULL);

    g_slist_free(edt->pi.proto_data);
    free_data_sources(&edt->pi);

    if (edt->tvb != NULL) {
        tvb_free_chain(edt->tvb);
    }

    if (edt->tree != NULL) {
        proto_tree_free(edt->tree);
    }

    if (ws_pinfo_pool_cache == NULL) {
        wmem_free_all(edt->pi.pool);
        ws_pinfo_pool_cache = edt->pi.pool;
    } else {
        wmem_destroy_allocator(edt->pi.pool);
    }
}

void
epan_dissect_free(epan_dissect_t *edt)
{
    epan_dissect_cleanup(edt);
    g_free(edt);
}

const char *
epan_custom_set(epan_dissect_t *edt, GSList *ids, int occurrence, bool display_details, char *result, char *expr, const int size)
{
    return proto_custom_set(edt->tree, ids, occurrence, display_details, result, expr, size);
}

void
epan_gather_compile_info(feature_list l _U_)
{
}

void
epan_gather_runtime_info(feature_list l _U_)
{
}
