package ai.chat2db.plugin.postgresql.value.template;

import ai.chat2db.plugin.postgresql.PostgreSqlEscapes;

import static ai.chat2db.plugin.postgresql.constant.PostgreSQLDmlValueTemplateConstants.*;



public class PostgreSQLDmlValueTemplate {



    public static String wrapBit(String value) {
        return String.format(BIT_TEMPLATE, PostgreSqlEscapes.requireBitLiteral(value));
    }
    public static String wrapBytea(String value) {
        return String.format(BYTEA_VALUE, PostgreSqlEscapes.requireHexLiteral(value));
    }

    public static String wrapJsonb(String value) {
        return String.format(JSONB_TEMPLATE, PostgreSqlEscapes.escapeSqlLiteral(value));
    }

    public static String wrapJson(String value) {
        return String.format(JSON_TEMPLATE, PostgreSqlEscapes.escapeSqlLiteral(value));
    }
}
