#include "packet-gsm_a_common.h"

#include <epan/expert.h>

typedef const elem_fcn *elem_func_handler_t;

typedef struct ws_core_elem_dispatch {
    const value_string_ext *names_ext;
    int *elem_ett;
    elem_func_handler_t elem_funcs;
    int hf_elem_id;
    bool supported;
} ws_core_elem_dispatch_t;

static int hf_gsm_a_l_ext;
static int hf_gsm_a_length;
static int hf_gsm_a_element_value;
static int hf_gsm_a_common_elem_id_f0;
static int hf_gsm_a_spare_nibble;
static int hf_gsm_a_gm_gprs_timer;
static int hf_gsm_a_gm_gprs_timer_unit;
static int hf_gsm_a_gm_gprs_timer_value;
static int hf_gsm_a_gm_gprs_timer3;
static int hf_gsm_a_gm_gprs_timer3_unit;
static int hf_gsm_a_gm_gprs_timer3_value;
static int proto_ws_core_gsm_a_common_minimal;

static int ett_ws_core_seed_generic;
static int ett_gmm_gprs_timer;

static expert_field ei_gsm_a_unknown_element;
static expert_field ei_gsm_a_unknown_pdu_type;
static expert_field ei_gsm_a_no_element_dissector;

static const value_string gsm_a_gm_gprs_timer_unit_vals[] = {
    { 0x00, "value is incremented in multiples of 2 seconds" },
    { 0x01, "value is incremented in multiples of 1 minute" },
    { 0x02, "value is incremented in multiples of decihours" },
    { 0x07, "value indicates that the timer is deactivated" },
    { 0, NULL }
};

static const value_string gsm_a_gm_gprs_timer3_unit_vals[] = {
    { 0x00, "value is incremented in multiples of 10 minutes" },
    { 0x01, "value is incremented in multiples of 1 hour" },
    { 0x02, "value is incremented in multiples of 10 hours" },
    { 0x03, "value is incremented in multiples of 2 seconds" },
    { 0x04, "value is incremented in multiples of 30 seconds" },
    { 0x05, "value is incremented in multiples of 1 minute" },
    { 0x06, "value is incremented in multiples of 320 hours (for T3312/T3412 extended), 1 hour otherwise" },
    { 0x07, "value indicates that the timer is deactivated" },
    { 0, NULL }
};

static bool
ws_core_get_elem_dispatch(int pdu_type, ws_core_elem_dispatch_t *dispatch)
{
    switch (pdu_type) {
    case NAS_5GS_PDU_TYPE_COMMON:
        dispatch->names_ext = &nas_5gs_common_elem_strings_ext;
        dispatch->elem_ett = ett_nas_5gs_common_elem;
        dispatch->elem_funcs = nas_5gs_common_elem_fcn;
        dispatch->hf_elem_id = hf_nas_5gs_common_elem_id;
        return true;
    case NAS_PDU_TYPE_COMMON:
        dispatch->names_ext = &nas_eps_common_elem_strings_ext;
        dispatch->elem_ett = ett_nas_eps_common_elem;
        dispatch->elem_funcs = nas_eps_common_elem_fcn;
        dispatch->hf_elem_id = hf_nas_eps_common_elem_id;
        return true;
    case NAS_PDU_TYPE_EMM:
        dispatch->names_ext = &nas_emm_elem_strings_ext;
        dispatch->elem_ett = ett_nas_eps_emm_elem;
        dispatch->elem_funcs = emm_elem_fcn;
        dispatch->hf_elem_id = hf_nas_eps_emm_elem_id;
        return true;
    case NAS_PDU_TYPE_ESM:
        dispatch->names_ext = &nas_esm_elem_strings_ext;
        dispatch->elem_ett = ett_nas_eps_esm_elem;
        dispatch->elem_funcs = esm_elem_fcn;
        dispatch->hf_elem_id = hf_nas_eps_esm_elem_id;
        return true;
    case NAS_5GS_PDU_TYPE_MM:
        dispatch->names_ext = &nas_5gs_mm_elem_strings_ext;
        dispatch->elem_ett = ett_nas_5gs_mm_elem;
        dispatch->elem_funcs = nas_5gs_mm_elem_fcn;
        dispatch->hf_elem_id = hf_nas_5gs_mm_elem_id;
        return true;
    case NAS_5GS_PDU_TYPE_SM:
        dispatch->names_ext = &nas_5gs_sm_elem_strings_ext;
        dispatch->elem_ett = ett_nas_5gs_sm_elem;
        dispatch->elem_funcs = nas_5gs_sm_elem_fcn;
        dispatch->hf_elem_id = hf_nas_5gs_sm_elem_id;
        return true;
    case NAS_5GS_PDU_TYPE_UPDP:
        dispatch->names_ext = &nas_5gs_updp_elem_strings_ext;
        dispatch->elem_ett = ett_nas_5gs_updp_elem;
        dispatch->elem_funcs = nas_5gs_updp_elem_fcn;
        dispatch->hf_elem_id = hf_nas_5gs_updp_elem_id;
        return true;
    default:
        dispatch->names_ext = NULL;
        dispatch->elem_ett = &ett_ws_core_seed_generic;
        dispatch->elem_funcs = NULL;
        dispatch->hf_elem_id = hf_nas_5gs_common_elem_id;
        return false;
    }
}

static const char *
ws_core_get_elem_name(packet_info *pinfo, int pdu_type, int idx, const ws_core_elem_dispatch_t *dispatch)
{
    if (dispatch->supported) {
        return val_to_str_ext(pinfo->pool, idx, dispatch->names_ext, "Unknown (%u)");
    }

    return wmem_strdup_printf(pinfo->pool, "PDU %d element %d", pdu_type, idx);
}

static uint16_t
ws_core_call_elem_or_raw(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo, uint32_t offset, unsigned len,
    elem_func_handler_t elem_funcs, int idx, proto_item *item)
{
    if (len == 0) {
        return 0;
    }

    if (elem_funcs == NULL || elem_funcs[idx] == NULL) {
        proto_tree_add_item(tree, hf_gsm_a_element_value, tvb, offset, len, ENC_NA);
        return (uint16_t)len;
    }

    char *a_add_string = (char *)wmem_alloc(pinfo->pool, 1024);
    a_add_string[0] = '\0';

    uint16_t consumed = (*elem_funcs[idx])(tvb, tree, pinfo, offset, len, a_add_string, 1024);
    if (a_add_string[0] != '\0' && item != NULL) {
        proto_item_append_text(item, "%s", a_add_string);
    }
    return consumed;
}

const char*
get_gsm_a_msg_string(wmem_allocator_t* pool, int pdu_type, int idx)
{
    switch (pdu_type) {
    case NAS_5GS_PDU_TYPE_COMMON:
        return val_to_str_ext(pool, idx, &nas_5gs_common_elem_strings_ext, "NAS_5GS_PDU_TYPE_COMMON (%u)");
    case NAS_PDU_TYPE_COMMON:
        return val_to_str_ext(pool, idx, &nas_eps_common_elem_strings_ext, "NAS_PDU_TYPE_COMMON (%u)");
    case NAS_PDU_TYPE_EMM:
        return val_to_str_ext(pool, idx, &nas_emm_elem_strings_ext, "NAS_PDU_TYPE_EMM (%u)");
    case NAS_PDU_TYPE_ESM:
        return val_to_str_ext(pool, idx, &nas_esm_elem_strings_ext, "NAS_PDU_TYPE_ESM (%u)");
    case NAS_5GS_PDU_TYPE_MM:
        return val_to_str_ext(pool, idx, &nas_5gs_mm_elem_strings_ext, "NAS_5GS_PDU_TYPE_MM (%u)");
    case NAS_5GS_PDU_TYPE_SM:
        return val_to_str_ext(pool, idx, &nas_5gs_sm_elem_strings_ext, "NAS_5GS_PDU_TYPE_SM (%u)");
    case NAS_5GS_PDU_TYPE_UPDP:
        return val_to_str_ext(pool, idx, &nas_5gs_updp_elem_strings_ext, "NAS_5GS_PDU_TYPE_UPDP (%u)");
    default:
        return wmem_strdup_printf(pool, "PDU %d element %d", pdu_type, idx);
    }
}

uint16_t
elem_tlv(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo, uint8_t iei, int pdu_type, int idx, uint32_t offset, unsigned len _U_, const char *name_add)
{
    uint32_t curr_offset = offset;
    uint16_t consumed = 0;
    uint8_t oct = tvb_get_uint8(tvb, curr_offset);

    if (oct != iei) {
        return 0;
    }

    ws_core_elem_dispatch_t dispatch;
    dispatch.supported = ws_core_get_elem_dispatch(pdu_type, &dispatch);

    uint16_t parm_len = tvb_get_uint8(tvb, curr_offset + 1);
    const char *elem_name = ws_core_get_elem_name(pinfo, pdu_type, idx, &dispatch);

    proto_item *item;
    proto_tree *subtree = proto_tree_add_subtree_format(tree, tvb, curr_offset, parm_len + 2,
        dispatch.supported ? dispatch.elem_ett[idx] : ett_ws_core_seed_generic,
        &item, "%s%s", elem_name, (name_add == NULL) || (name_add[0] == '\0') ? "" : name_add);

    proto_tree_add_uint(subtree, dispatch.hf_elem_id, tvb, curr_offset, 1, oct);
    proto_tree_add_uint(subtree, hf_gsm_a_length, tvb, curr_offset + 1, 1, parm_len);

    consumed = ws_core_call_elem_or_raw(tvb, subtree, pinfo, curr_offset + 2, parm_len,
        dispatch.supported ? dispatch.elem_funcs : NULL, idx, item);

    return consumed + 2;
}

uint16_t
elem_tlv_e(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo, uint8_t iei, int pdu_type, int idx, uint32_t offset, unsigned len _U_, const char *name_add)
{
    uint32_t curr_offset = offset;
    uint16_t consumed = 0;
    uint8_t oct = tvb_get_uint8(tvb, curr_offset);

    if (oct != iei) {
        return 0;
    }

    ws_core_elem_dispatch_t dispatch;
    dispatch.supported = ws_core_get_elem_dispatch(pdu_type, &dispatch);

    uint16_t parm_len = tvb_get_ntohs(tvb, curr_offset + 1);
    const char *elem_name = ws_core_get_elem_name(pinfo, pdu_type, idx, &dispatch);

    proto_item *item;
    proto_tree *subtree = proto_tree_add_subtree_format(tree, tvb, curr_offset, parm_len + 3,
        dispatch.supported ? dispatch.elem_ett[idx] : ett_ws_core_seed_generic,
        &item, "%s%s", elem_name, (name_add == NULL) || (name_add[0] == '\0') ? "" : name_add);

    proto_tree_add_uint(subtree, dispatch.hf_elem_id, tvb, curr_offset, 1, oct);
    proto_tree_add_uint(subtree, hf_gsm_a_length, tvb, curr_offset + 1, 2, parm_len);

    consumed = ws_core_call_elem_or_raw(tvb, subtree, pinfo, curr_offset + 3, parm_len,
        dispatch.supported ? dispatch.elem_funcs : NULL, idx, item);

    return consumed + 3;
}

uint16_t
elem_tv(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo, uint8_t iei, int pdu_type, int idx, uint32_t offset, const char *name_add)
{
    uint32_t curr_offset = offset;
    uint16_t consumed = 0;
    uint8_t oct = tvb_get_uint8(tvb, curr_offset);

    if (oct != iei) {
        return 0;
    }

    ws_core_elem_dispatch_t dispatch;
    dispatch.supported = ws_core_get_elem_dispatch(pdu_type, &dispatch);

    const char *elem_name = ws_core_get_elem_name(pinfo, pdu_type, idx, &dispatch);
    proto_item *item;
    proto_tree *subtree = proto_tree_add_subtree_format(tree, tvb, curr_offset, -1,
        dispatch.supported ? dispatch.elem_ett[idx] : ett_ws_core_seed_generic,
        &item, "%s%s", elem_name, (name_add == NULL) || (name_add[0] == '\0') ? "" : name_add);

    proto_tree_add_uint(subtree, dispatch.hf_elem_id, tvb, curr_offset, 1, oct);

    if (!dispatch.supported || dispatch.elem_funcs == NULL || dispatch.elem_funcs[idx] == NULL) {
        expert_add_info(pinfo, item, &ei_gsm_a_no_element_dissector);
        consumed = 1;
    } else {
        consumed = ws_core_call_elem_or_raw(tvb, subtree, pinfo, curr_offset + 1, (unsigned)-1, dispatch.elem_funcs, idx, item);
        consumed++;
    }

    proto_item_set_len(item, consumed);
    return consumed;
}

uint16_t
elem_tv_short(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo, uint8_t iei, int pdu_type, int idx, uint32_t offset, const char *name_add)
{
    uint32_t curr_offset = offset;
    uint8_t oct = tvb_get_uint8(tvb, curr_offset);
    if ((oct & 0xf0) != (iei & 0xf0)) {
        return 0;
    }

    ws_core_elem_dispatch_t dispatch;
    dispatch.supported = ws_core_get_elem_dispatch(pdu_type, &dispatch);

    const char *elem_name = ws_core_get_elem_name(pinfo, pdu_type, idx, &dispatch);
    proto_item *item;
    proto_tree *subtree = proto_tree_add_subtree_format(tree, tvb, curr_offset, -1,
        dispatch.supported ? dispatch.elem_ett[idx] : ett_ws_core_seed_generic,
        &item, "%s%s", elem_name, (name_add == NULL) || (name_add[0] == '\0') ? "" : name_add);

    proto_tree_add_uint_format_value(subtree, hf_gsm_a_common_elem_id_f0, tvb, curr_offset, 1, oct, "0x%1x-", oct >> 4);

    if (!dispatch.supported || dispatch.elem_funcs == NULL || dispatch.elem_funcs[idx] == NULL) {
        expert_add_info(pinfo, item, &ei_gsm_a_no_element_dissector);
        proto_item_set_len(item, 1);
        return 1;
    }

    char *a_add_string = (char *)wmem_alloc(pinfo->pool, 1024);
    a_add_string[0] = '\0';
    uint16_t consumed = (*dispatch.elem_funcs[idx])(tvb, subtree, pinfo, curr_offset, RIGHT_NIBBLE, a_add_string, 1024);
    if (a_add_string[0] != '\0') {
        proto_item_append_text(item, "%s", a_add_string);
    }
    proto_item_set_len(item, consumed);
    return consumed;
}

uint16_t
elem_t(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo, uint8_t iei, int pdu_type, int idx, uint32_t offset, const char *name_add)
{
    uint8_t oct = tvb_get_uint8(tvb, offset);
    if (oct != iei) {
        return 0;
    }

    ws_core_elem_dispatch_t dispatch;
    dispatch.supported = ws_core_get_elem_dispatch(pdu_type, &dispatch);

    proto_tree_add_uint_format(tree, dispatch.hf_elem_id, tvb, offset, 1, oct, "%s%s",
        ws_core_get_elem_name(pinfo, pdu_type, idx, &dispatch),
        (name_add == NULL) || (name_add[0] == '\0') ? "" : name_add);
    return 1;
}

uint16_t
elem_lv(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo, int pdu_type, int idx, uint32_t offset, unsigned len _U_, const char *name_add)
{
    ws_core_elem_dispatch_t dispatch;
    dispatch.supported = ws_core_get_elem_dispatch(pdu_type, &dispatch);

    uint8_t parm_len = tvb_get_uint8(tvb, offset);
    const char *elem_name = ws_core_get_elem_name(pinfo, pdu_type, idx, &dispatch);

    proto_item *item;
    proto_tree *subtree = proto_tree_add_subtree_format(tree, tvb, offset, parm_len + 1,
        dispatch.supported ? dispatch.elem_ett[idx] : ett_ws_core_seed_generic,
        &item, "%s%s", elem_name, (name_add == NULL) || (name_add[0] == '\0') ? "" : name_add);

    proto_tree_add_uint(subtree, hf_gsm_a_length, tvb, offset, 1, parm_len);
    uint16_t consumed = ws_core_call_elem_or_raw(tvb, subtree, pinfo, offset + 1, parm_len,
        dispatch.supported ? dispatch.elem_funcs : NULL, idx, item);
    return consumed + 1;
}

uint16_t
elem_lv_e(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo, int pdu_type, int idx, uint32_t offset, unsigned len _U_, const char *name_add)
{
    ws_core_elem_dispatch_t dispatch;
    dispatch.supported = ws_core_get_elem_dispatch(pdu_type, &dispatch);

    uint16_t parm_len = tvb_get_ntohs(tvb, offset);
    const char *elem_name = ws_core_get_elem_name(pinfo, pdu_type, idx, &dispatch);

    proto_item *item;
    proto_tree *subtree = proto_tree_add_subtree_format(tree, tvb, offset, parm_len + 2,
        dispatch.supported ? dispatch.elem_ett[idx] : ett_ws_core_seed_generic,
        &item, "%s%s", elem_name, (name_add == NULL) || (name_add[0] == '\0') ? "" : name_add);

    proto_tree_add_uint(subtree, hf_gsm_a_length, tvb, offset, 2, parm_len);
    uint16_t consumed = ws_core_call_elem_or_raw(tvb, subtree, pinfo, offset + 2, parm_len,
        dispatch.supported ? dispatch.elem_funcs : NULL, idx, item);
    return consumed + 2;
}

uint16_t
elem_v(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo, int pdu_type, int idx, uint32_t offset, const char *name_add)
{
    ws_core_elem_dispatch_t dispatch;
    dispatch.supported = ws_core_get_elem_dispatch(pdu_type, &dispatch);

    if (!dispatch.supported || dispatch.elem_funcs == NULL || dispatch.elem_funcs[idx] == NULL) {
        proto_tree_add_expert(tree, pinfo, &ei_gsm_a_no_element_dissector, tvb, offset, 1);
        return 1;
    }

    const char *elem_name = ws_core_get_elem_name(pinfo, pdu_type, idx, &dispatch);
    proto_item *item;
    proto_tree *subtree = proto_tree_add_subtree(tree, tvb, offset, 0, dispatch.elem_ett[idx], &item, elem_name);

    char *a_add_string = (char *)wmem_alloc(pinfo->pool, 1024);
    a_add_string[0] = '\0';
    uint16_t consumed = (*dispatch.elem_funcs[idx])(tvb, subtree, pinfo, offset, (unsigned)-1, a_add_string, 1024);
    if (a_add_string[0] != '\0') {
        proto_item_append_text(item, "%s", a_add_string);
    }
    proto_item_set_len(item, consumed);
    return consumed;
}

uint16_t
de_spare_nibble(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo _U_, uint32_t offset, unsigned len, char *add_string _U_, int string_len _U_)
{
    int bit_offset = (RIGHT_NIBBLE == len) ? 4 : 0;
    proto_tree_add_bits_item(tree, hf_gsm_a_spare_nibble, tvb, (offset << 3) + bit_offset, 4, ENC_BIG_ENDIAN);
    return 1;
}

uint16_t
elem_v_short(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo, int pdu_type, int idx, uint32_t offset, uint32_t nibble)
{
    ws_core_elem_dispatch_t dispatch;
    dispatch.supported = ws_core_get_elem_dispatch(pdu_type, &dispatch);

    const char *elem_name = ws_core_get_elem_name(pinfo, pdu_type, idx, &dispatch);
    proto_item *item;
    proto_tree *subtree = proto_tree_add_subtree(tree, tvb, offset, 0,
        dispatch.supported ? dispatch.elem_ett[idx] : ett_ws_core_seed_generic, &item, elem_name);

    char *a_add_string = (char *)wmem_alloc(pinfo->pool, 1024);
    a_add_string[0] = '\0';

    if (!dispatch.supported || dispatch.elem_funcs == NULL || dispatch.elem_funcs[idx] == NULL) {
        (void)de_spare_nibble(tvb, subtree, pinfo, offset, nibble, a_add_string, 1024);
    } else {
        (void)(*dispatch.elem_funcs[idx])(tvb, subtree, pinfo, offset, nibble, a_add_string, 1024);
    }

    if (a_add_string[0] != '\0') {
        proto_item_append_text(item, "%s", a_add_string);
    }
    proto_item_set_len(item, 1);
    return 1;
}

uint16_t
de_gc_timer(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo _U_, uint32_t offset, unsigned len _U_, char *add_string _U_, int string_len _U_)
{
    uint8_t oct = tvb_get_uint8(tvb, offset);
    uint16_t val = oct & 0x1f;
    const char *str = NULL;
    proto_item *item = NULL;

    switch (oct >> 5) {
    case 0:
        str = "sec";
        val *= 2;
        break;
    case 1:
        str = "min";
        break;
    case 2:
        str = "min";
        val *= 6;
        break;
    case 7:
        item = proto_tree_add_uint_format_value(tree, hf_gsm_a_gm_gprs_timer, tvb, offset, 1, val, "timer is deactivated");
        break;
    default:
        str = "min";
        break;
    }

    if (item == NULL) {
        item = proto_tree_add_uint_format_value(tree, hf_gsm_a_gm_gprs_timer, tvb, offset, 1, val, "%u %s", val, str);
    }

    proto_tree *subtree = proto_item_add_subtree(item, ett_gmm_gprs_timer);
    proto_tree_add_item(subtree, hf_gsm_a_gm_gprs_timer_unit, tvb, offset, 1, ENC_BIG_ENDIAN);
    proto_tree_add_item(subtree, hf_gsm_a_gm_gprs_timer_value, tvb, offset, 1, ENC_BIG_ENDIAN);
    return 1;
}

uint16_t
de_gc_timer3(tvbuff_t *tvb, proto_tree *tree, packet_info *pinfo _U_, uint32_t offset, unsigned len _U_, char *add_string _U_, int string_len _U_)
{
    uint8_t oct = tvb_get_uint8(tvb, offset);
    uint16_t val = oct & 0x1f;
    const char *str = NULL;
    proto_item *item = NULL;

    switch (oct >> 5) {
    case 0:
        str = "min";
        val *= 10;
        break;
    case 1:
        str = (val == 1) ? "hour" : "hours";
        break;
    case 2:
        str = "hours";
        val *= 10;
        break;
    case 3:
        str = "sec";
        val *= 2;
        break;
    case 4:
        str = "sec";
        val *= 30;
        break;
    case 5:
        str = "min";
        break;
    case 6:
        str = "hours";
        val *= 320;
        break;
    case 7:
        item = proto_tree_add_uint_format_value(tree, hf_gsm_a_gm_gprs_timer3, tvb, offset, 1, val, "timer is deactivated");
        break;
    }

    if (item == NULL) {
        item = proto_tree_add_uint_format_value(tree, hf_gsm_a_gm_gprs_timer3, tvb, offset, 1, val, "%u %s", val, str);
    }

    proto_tree *subtree = proto_item_add_subtree(item, ett_gmm_gprs_timer);
    proto_tree_add_item(subtree, hf_gsm_a_gm_gprs_timer3_unit, tvb, offset, 1, ENC_BIG_ENDIAN);
    proto_tree_add_item(subtree, hf_gsm_a_gm_gprs_timer3_value, tvb, offset, 1, ENC_BIG_ENDIAN);
    return 1;
}

void
proto_register_ws_core_gsm_a_common_minimal(void)
{
    static hf_register_info hf[] = {
        { &hf_gsm_a_l_ext, { "Extended length", "gsm_a.l_ext", FT_BOOLEAN, 8, NULL, 0x80, NULL, HFILL } },
        { &hf_gsm_a_length, { "Length", "gsm_a.length", FT_UINT16, BASE_DEC, NULL, 0x0, NULL, HFILL } },
        { &hf_gsm_a_element_value, { "Element value", "gsm_a.element_value", FT_BYTES, BASE_NONE, NULL, 0x0, NULL, HFILL } },
        { &hf_gsm_a_common_elem_id_f0, { "Element identifier", "gsm_a.common_elem_id_f0", FT_UINT8, BASE_HEX, NULL, 0xF0, NULL, HFILL } },
        { &hf_gsm_a_spare_nibble, { "Spare nibble", "gsm_a.spare_nibble", FT_UINT8, BASE_HEX, NULL, 0x0F, NULL, HFILL } },
        { &hf_gsm_a_gm_gprs_timer, { "GPRS timer", "gsm_a.gprs_timer", FT_UINT16, BASE_DEC, NULL, 0x0, NULL, HFILL } },
        { &hf_gsm_a_gm_gprs_timer_unit, { "GPRS timer unit", "gsm_a.gprs_timer.unit", FT_UINT8, BASE_DEC, VALS(gsm_a_gm_gprs_timer_unit_vals), 0xE0, NULL, HFILL } },
        { &hf_gsm_a_gm_gprs_timer_value, { "GPRS timer value", "gsm_a.gprs_timer.value", FT_UINT8, BASE_DEC, NULL, 0x1F, NULL, HFILL } },
        { &hf_gsm_a_gm_gprs_timer3, { "GPRS timer 3", "gsm_a.gprs_timer3", FT_UINT16, BASE_DEC, NULL, 0x0, NULL, HFILL } },
        { &hf_gsm_a_gm_gprs_timer3_unit, { "GPRS timer 3 unit", "gsm_a.gprs_timer3.unit", FT_UINT8, BASE_DEC, VALS(gsm_a_gm_gprs_timer3_unit_vals), 0xE0, NULL, HFILL } },
        { &hf_gsm_a_gm_gprs_timer3_value, { "GPRS timer 3 value", "gsm_a.gprs_timer3.value", FT_UINT8, BASE_DEC, NULL, 0x1F, NULL, HFILL } },
    };
    static int *ett[] = {
        &ett_ws_core_seed_generic,
        &ett_gmm_gprs_timer,
    };
    static ei_register_info ei[] = {
        { &ei_gsm_a_unknown_element, { "gsm_a.unknown_element", PI_PROTOCOL, PI_WARN, "Unknown element", EXPFILL } },
        { &ei_gsm_a_unknown_pdu_type, { "gsm_a.unknown_pdu_type", PI_PROTOCOL, PI_WARN, "Unknown PDU type", EXPFILL } },
        { &ei_gsm_a_no_element_dissector, { "gsm_a.no_element_dissector", PI_PROTOCOL, PI_WARN, "No element dissector registered", EXPFILL } },
    };
    expert_module_t *expert_module;

    proto_ws_core_gsm_a_common_minimal = proto_register_protocol(
        "GSM A Common Minimal",
        "GSM_A_COMMON_MIN",
        "gsm_a_common_min");

    proto_register_field_array(proto_ws_core_gsm_a_common_minimal, hf, G_N_ELEMENTS(hf));
    proto_register_subtree_array(ett, G_N_ELEMENTS(ett));
    expert_module = expert_register_protocol(proto_ws_core_gsm_a_common_minimal);
    expert_register_field_array(expert_module, ei, G_N_ELEMENTS(ei));
}
