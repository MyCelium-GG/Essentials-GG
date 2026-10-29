package my.celium.org.essentialsgg.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough YAML to read EssentialsX data files: nested maps of scalars by
 * indentation, quoted or plain values, comments. Lists, anchors and
 * multi-line strings are skipped. Values come back as strings; nested maps
 * as {@code Map<String, Object>}.
 */
public final class MiniYaml {
    private MiniYaml() {
    }

    public static Map<String, Object> parse(String text) {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> stack = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();
        stack.add(root);
        indents.add(-1);
        for (String rawLine : text.split("\r?\n")) {
            String line = stripComment(rawLine);
            if (line.isBlank() || line.trim().startsWith("- ") || line.trim().equals("-")) {
                continue;
            }
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }
            String content = line.substring(indent);
            int colon = keyEnd(content);
            if (colon < 0) {
                continue;
            }
            while (indents.size() > 1 && indent <= indents.get(indents.size() - 1)) {
                indents.remove(indents.size() - 1);
                stack.remove(stack.size() - 1);
            }
            String key = unquote(content.substring(0, colon).trim());
            String value = content.substring(colon + 1).trim();
            Map<String, Object> parent = stack.get(stack.size() - 1);
            if (value.isEmpty()) {
                Map<String, Object> child = new LinkedHashMap<>();
                parent.put(key, child);
                stack.add(child);
                indents.add(indent);
            } else if (!value.equals("[]") && !value.equals("{}")) {
                parent.put(key, unquote(value));
            }
        }
        return root;
    }

    /** Position of the {@code :} ending the key (outside quotes, followed by space or end), or -1. */
    private static int keyEnd(String content) {
        char quote = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ':' && (i + 1 == content.length() || content.charAt(i + 1) == ' ')) {
                return i;
            }
        }
        return -1;
    }

    private static String stripComment(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '#' && (i == 0 || line.charAt(i - 1) == ' ')) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if (first == '\'' && last == '\'') {
                return value.substring(1, value.length() - 1).replace("''", "'");
            }
            if (first == '"' && last == '"') {
                return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            }
        }
        return value;
    }

    /** The map at {@code key}, or an empty one. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    public static String string(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value instanceof String s ? s : null;
    }

    public static double number(Map<String, Object> parent, String key, double fallback) {
        String value = string(parent, key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
