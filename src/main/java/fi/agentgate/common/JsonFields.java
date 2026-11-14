package fi.agentgate.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Tolerant JSON readers. Valio's exact field names are not confirmed yet, so each reader
 * tries several candidate names. Once the real schema is known, pass just the right name.
 */
public final class JsonFields {

    private JsonFields() {
    }

    public static String text(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v != null && !v.isNull() && !v.isContainerNode() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }

    public static BigDecimal decimal(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isNumber()) {
                return v.decimalValue();
            }
            if (v.isTextual()) {
                try {
                    return new BigDecimal(v.asText().trim().replace(',', '.'));
                } catch (NumberFormatException ignored) {
                    // try the next candidate
                }
            }
        }
        return null;
    }

    public static Boolean bool(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v != null && v.isBoolean()) {
                return v.booleanValue();
            }
        }
        return null;
    }

    public static LocalDate date(JsonNode node, String... names) {
        String s = text(node, names);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Reads either a JSON array of strings or a comma/semicolon separated string. */
    public static List<String> list(JsonNode node, String... names) {
        if (node == null) {
            return List.of();
        }
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v == null || v.isNull()) {
                continue;
            }
            List<String> out = new ArrayList<>();
            if (v.isArray()) {
                v.forEach(item -> {
                    String s = item.isTextual() ? item.asText() : text(item, "name", "code", "value");
                    if (s != null && !s.isBlank()) {
                        out.add(s.trim());
                    }
                });
            } else if (v.isTextual()) {
                Arrays.stream(v.asText().split("[,;]"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(out::add);
            }
            return out;
        }
        return List.of();
    }

    /** True when at least one of the candidate fields exists (even if empty). */
    public static boolean has(JsonNode node, String... names) {
        if (node == null) {
            return false;
        }
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v != null && !v.isNull()) {
                return true;
            }
        }
        return false;
    }

    /** If the node is an array, returns its first element; otherwise the node itself. */
    public static JsonNode firstObject(JsonNode node) {
        if (node != null && node.isArray()) {
            return node.isEmpty() ? null : node.get(0);
        }
        return node;
    }
}
