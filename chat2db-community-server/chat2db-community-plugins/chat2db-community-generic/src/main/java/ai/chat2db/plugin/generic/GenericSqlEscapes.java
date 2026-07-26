package ai.chat2db.plugin.generic;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Canonical escaping/validation helpers for values substituted into generic adapter SQL
 * templates (generic.json sqlMap) (#1914).
 *
 * The generic adapter serves mixed dialects via DBConfig templates (e.g. DuckDB wraps
 * placeholders in single quotes, TDengine uses bare identifier positions), so treatment
 * is chosen per placeholder by inspecting the template; no single dialect quote char is
 * hard-coded.
 */
public final class GenericSqlEscapes {

    private static final Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_$]+$");

    private GenericSqlEscapes() {
    }

    /**
     * Escape a value interpolated into a single-quoted SQL string literal (surrounding
     * quotes NOT added). Standard single-quote doubling.
     */
    public static String escapeSqlLiteral(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("'", "''");
    }

    /**
     * Quote an identifier with the dialect's quote char: strips one surrounding pair of
     * that quote, then doubles every embedded quote char.
     */
    public static String quoteIdentifier(String name, char quote) {
        if (StringUtils.isBlank(name)) {
            return name;
        }
        String q = String.valueOf(quote);
        String identifier = name;
        if (identifier.length() >= 2 && identifier.startsWith(q) && identifier.endsWith(q)) {
            identifier = identifier.substring(1, identifier.length() - 1);
        }
        return q + identifier.replace(q, q + q) + q;
    }

    /**
     * Validate a strict identifier token for bare-identifier template positions, where the
     * generic adapter cannot know the dialect's identifier quote char.
     */
    public static String requireSafeIdentifier(String value, String what) {
        if (value == null || !SAFE_IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid generic " + what + ": " + value);
        }
        return value;
    }

    /**
     * Sanitize a value that DBConfig substitutes for {@code placeholder} in the given
     * generic.json SQL template. A placeholder wrapped in single quotes ('{database}')
     * lands in string-literal position and gets literal escaping; a bare placeholder
     * ({database}) lands in identifier position and must pass the identifier whitelist.
     */
    public static String sanitizeTemplateValue(String template, String placeholder, String value) {
        if (template == null || StringUtils.isBlank(value)) {
            return value;
        }
        if (template.contains("'" + placeholder + "'")) {
            return escapeSqlLiteral(value);
        }
        return requireSafeIdentifier(value, placeholder);
    }
}
