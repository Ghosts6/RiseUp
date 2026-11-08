package com.riseup.cli.format;
import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.cli.command.Result;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TableFormatter implements OutputFormatter {
    @Override public String format(Result result) {
        List<String> lines = new ArrayList<>();
        Object data = result.data();
        if (data instanceof JsonNode node) lines.add(formatNode(node));
        else if (data instanceof Map<?, ?> map) lines.add(String.valueOf(map));
        else if (data != null) lines.add(String.valueOf(data));
        if (result.message() != null && !result.message().isBlank()) lines.add(result.message());
        return String.join("\n", lines).stripTrailing();
    }
    private String formatNode(JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (node.isArray()) {
            if (node.isEmpty()) return "(empty)";
            if (node.get(0).isObject()) {
                List<String> cols = new ArrayList<>();
                Iterator<String> it = node.get(0).fieldNames();
                while (it.hasNext()) cols.add(it.next());
                StringBuilder sb = new StringBuilder();
                sb.append(String.join(" | ", cols)).append('\n');
                sb.append("-".repeat(Math.max(8, cols.size() * 12))).append('\n');
                for (JsonNode row : node) {
                    List<String> cells = new ArrayList<>();
                    for (String c : cols) {
                        JsonNode v = row.get(c);
                        cells.add(v == null || v.isNull() ? "-" : v.isContainerNode() ? v.toString() : v.asText());
                    }
                    sb.append(String.join(" | ", cells)).append('\n');
                }
                return sb.toString().stripTrailing();
            }
        }
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder();
            appendObject(sb, node, "  ");
            return sb.toString().stripTrailing();
        }
        return node.asText();
    }

    /** key : value lines; nested objects become indented sub-blocks instead of blank values. */
    private void appendObject(StringBuilder sb, JsonNode obj, String indent) {
        obj.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v.isContainerNode() && v.isEmpty()) {
                sb.append(indent).append(e.getKey()).append(" : (none)").append('\n');
            } else if (v.isObject()) {
                sb.append(indent).append(e.getKey()).append(" :").append('\n');
                appendObject(sb, v, indent + "  ");
            } else if (v.isArray()) {
                boolean scalars = true;
                for (JsonNode item : v) scalars &= !item.isContainerNode();
                if (scalars) {
                    List<String> items = new ArrayList<>();
                    for (JsonNode item : v) items.add(item.asText());
                    sb.append(indent).append(e.getKey()).append(" : ").append(String.join(", ", items)).append('\n');
                } else {
                    sb.append(indent).append(e.getKey()).append(" :").append('\n');
                    for (JsonNode item : v) {
                        sb.append(indent).append("  -").append('\n');
                        appendObject(sb, item, indent + "    ");
                    }
                }
            } else {
                sb.append(indent).append(e.getKey()).append(" : ").append(v.isNull() ? "-" : v.asText()).append('\n');
            }
        });
    }
}
