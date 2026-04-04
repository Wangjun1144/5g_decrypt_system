#ifndef WS_CORE_JSON_EXPORT_MINIMAL_H
#define WS_CORE_JSON_EXPORT_MINIMAL_H

#include <glib.h>

#include <epan/proto.h>

gboolean
ws_core_export_grouped_json_tree(proto_node *root, GString *json);

#endif
