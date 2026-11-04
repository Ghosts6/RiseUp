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
                        cells.add(v == null || v.isNull() ? "-" : v.asText());
                    }
                    sb.append(String.join(" | ", cells)).append('\n');
                }
                return sb.toString().stripTrailing();
            }
        }
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder();
            node.fields().forEachRemaining(e ->
                sb.append("  ").append(e.getKey()).append(" : ").append(e.getValue().asText()).append('\n'));
            return sb.toString().stripTrailing();
        }
        return node.asText();
    }
}
