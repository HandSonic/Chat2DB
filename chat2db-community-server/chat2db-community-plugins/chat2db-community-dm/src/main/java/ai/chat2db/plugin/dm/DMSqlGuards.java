package ai.chat2db.plugin.dm;

import java.util.regex.Pattern;

/**
 * Validation helpers for non-escapable SQL positions in DM DDL generation
 * (column DEFAULT expressions emitted verbatim). Escaping itself lives in
 * {@link ai.chat2db.plugin.dm.identifier.DMIdentifierProcessor}.
 */
public final class DMSqlGuards {

    /**
     * Legitimate column DEFAULT expressions: quoted string literals (with ''
     * escapes), numeric literals, or keyword/function forms such as
     * CURRENT_TIMESTAMP, SYSDATE, USER, SEQ.NEXTVAL. Function-call arguments
     * tolerate quoted string literals and one level of nested parentheses
     * (e.g. NVL(SUM(x),0)); semicolons are never allowed. Anything else is
     * rejected because DEFAULT values are emitted verbatim.
     */
    private static final Pattern DEFAULT_EXPRESSION = Pattern.compile(
            "'[^']*(?:''[^']*)*'|[+-]?(\\d+(\\.\\d+)?|\\.\\d+)|[A-Za-z_][A-Za-z0-9_]*([.][A-Za-z_][A-Za-z0-9_]*)*(\\s*\\((?:'[^']*(?:''[^']*)*'|\\((?:'[^']*(?:''[^']*)*'|[^()';])*\\)|[^()';])*\\))?");

    private DMSqlGuards() {
    }

    /**
     * Validates a column DEFAULT expression that is emitted verbatim into DDL.
     * Accepts quoted string literals (escaped via doubling), numeric literals,
     * and keyword/function forms; rejects everything else.
     */
    public static String requireDefaultExpression(String defaultValue) {
        String trimmed = defaultValue.trim();
        if (!DEFAULT_EXPRESSION.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid DM default expression: " + defaultValue);
        }
        return trimmed;
    }
}
