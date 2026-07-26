package ai.chat2db.plugin.mongodb;

import java.util.regex.Pattern;

/**
 * Neutralization helpers for values interpolated into Mongo shell command text (#1914).
 * Shell commands are not plain SQL: database/collection names are validated against a strict
 * allowlist, and string values inside JSON documents are JSON-escaped.
 */
public final class MongodbSqlEscapes {

    private static final Pattern MONGO_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_$-]+$");

    private MongodbSqlEscapes() {
    }

    /**
     * Validate a database/collection/field name interpolated into shell command text such as
     * {@code use <name>} or {@code db.<name>.find()}. Rejects anything outside the allowlist.
     */
    public static String requireMongoName(String name, String what) {
        if (name == null || !MONGO_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid MongoDB " + what + ": " + name);
        }
        return name;
    }

    /**
     * Escape a value interpolated into a double-quoted JSON string inside a shell command
     * (surrounding quotes NOT added).
     */
    public static String escapeJsonString(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
