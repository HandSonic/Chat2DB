package ai.chat2db.plugin.clickhouse;

import ai.chat2db.plugin.clickhouse.identifier.ClickHouseIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * Validation helpers for non-escapable SQL positions in ClickHouse DDL
 * generation (column type expressions, table engines, column default
 * expressions). Escaping itself lives in {@link ClickHouseIdentifierProcessor}.
 */
public final class ClickHouseSqlGuards {

    private ClickHouseSqlGuards() {
    }

    /**
     * Validates a column type expression that is emitted verbatim into DDL
     * (e.g. Int32, Decimal(10,2), Array(Nullable(String)), Enum8('a'=1)).
     * Only letters/digits/underscore are allowed at the top level; spaces,
     * commas, single quotes and equals signs are only allowed inside balanced
     * parentheses. Semicolons, dashes, double quotes and backticks are always
     * rejected.
     */
    public static String requireColumnTypeExpression(String columnType) {
        if (StringUtils.isBlank(columnType)) {
            throw new IllegalArgumentException("Invalid ClickHouse column type: " + columnType);
        }
        int depth = 0;
        for (int i = 0; i < columnType.length(); i++) {
            char c = columnType.charAt(i);
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')') {
                depth--;
                if (depth < 0) {
                    throw new IllegalArgumentException("Invalid ClickHouse column type: " + columnType);
                }
                continue;
            }
            boolean ok = Character.isLetterOrDigit(c) || c == '_'
                    || (depth > 0 && (c == ' ' || c == ',' || c == '\'' || c == '='));
            if (!ok) {
                throw new IllegalArgumentException("Invalid ClickHouse column type: " + columnType);
            }
        }
        if (depth != 0 || !Character.isLetter(columnType.charAt(0))) {
            throw new IllegalArgumentException("Invalid ClickHouse column type: " + columnType);
        }
        return columnType;
    }

    /**
     * Validates a table engine expression emitted verbatim into CREATE TABLE
     * DDL (e.g. MergeTree, ReplicatedMergeTree('/path','replica')). Only a
     * dotted-free identifier with an optional balanced argument list is
     * accepted; semicolons are never allowed inside the arguments.
     */
    public static String requireEngine(String engine) {
        if (!engine.matches("[A-Za-z0-9_]+(\\s*\\([^;)]*\\))?")) {
            throw new IllegalArgumentException("Invalid ClickHouse engine: " + engine);
        }
        return engine;
    }

    /**
     * Renders a column default expression safe for inclusion in generated DDL:
     * values wrapped in single quotes are treated as string literals whose
     * inner content is re-escaped; unquoted values must match a conservative
     * allow-list (numbers, identifiers, function calls without semicolons);
     * anything else is rejected (fail closed).
     */
    public static String escapeDefaultExpression(String defaultValue) {
        String trimmed = defaultValue.trim();
        // Quoted string literals stay literals; the content is escaped before re-quoting.
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return "'" + ClickHouseIdentifierProcessor.INSTANCE.escapeString(trimmed.substring(1, trimmed.length() - 1)) + "'";
        }
        if (!trimmed.matches("[-+]?(\\d+(\\.\\d+)?|[A-Za-z_][A-Za-z0-9_]*(\\s*\\([^;)]*\\))?)")) {
            throw new IllegalArgumentException("Invalid ClickHouse default expression: " + defaultValue);
        }
        return trimmed;
    }
}
