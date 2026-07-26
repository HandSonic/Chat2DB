package ai.chat2db.plugin.snowflake;

import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.snowflake.builder.SnowflakeSqlBuilder;
import ai.chat2db.plugin.snowflake.enums.type.SnowflakeColumnTypeEnum;
import ai.chat2db.plugin.snowflake.enums.type.SnowflakeIndexTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", SnowflakeSqlEscapes.escapeSqlLiteral("O'Brien"));
        assertEquals("plain", SnowflakeSqlEscapes.escapeSqlLiteral("plain"));
    }

    @Test
    void escapeIdentifierDoublesDoubleQuotes() {
        assertEquals("we\"\"ird", SnowflakeSqlEscapes.escapeIdentifier("we\"ird"));
    }

    @Test
    void quoteIdentifierStripsOnePairThenDoublesEmbeddedQuotes() {
        assertEquals("\"users\"", SnowflakeSqlEscapes.quoteIdentifier("users"));
        assertEquals("\"we\"\"ird\"", SnowflakeSqlEscapes.quoteIdentifier("we\"ird"));
        assertEquals("\"ta\"\"ble\"", SnowflakeSqlEscapes.quoteIdentifier("\"ta\"ble\""));
    }

    @Test
    void buildCreateTableNeutralizesMaliciousTableNameAndComment() {
        SnowflakeSqlBuilder builder = new SnowflakeSqlBuilder();
        Table table = tableWithColumn("name", "VARCHAR");
        table.setName("users\"; DROP TABLE t; --");
        table.setComment("x'; DROP TABLE t; --");

        String sql = builder.buildCreateTable(table, new TableBuilderConfig());

        assertTrue(sql.contains("\"users\"\"; DROP TABLE t; --\""), sql);
        assertTrue(sql.contains("COMMENT='x''; DROP TABLE t; --'"), sql);
    }

    @Test
    void buildCreateColumnSqlNeutralizesMaliciousColumnNameAndComment() {
        TableColumn column = new TableColumn();
        column.setName("c\"ol");
        column.setColumnType("VARCHAR");
        column.setComment("c'); DROP TABLE t; --");

        String sql = SnowflakeColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);

        assertTrue(sql.startsWith("\"c\"\"ol\" "), sql);
        assertTrue(sql.contains("COMMENT 'c''); DROP TABLE t; --'"), sql);
        assertFalse(sql.contains("COMMENT 'c');"), sql);
    }

    @Test
    void buildIndexScriptNeutralizesMaliciousIndexAndColumnNames() {
        TableIndex index = new TableIndex();
        index.setName("idx\"x");
        index.setType("Normal");
        TableIndexColumn column = new TableIndexColumn();
        column.setColumnName("col\"x");
        column.setAscOrDesc("ASC");
        index.setColumnList(Collections.singletonList(column));

        String sql = SnowflakeIndexTypeEnum.NORMAL.buildIndexScript(index);

        assertTrue(sql.contains("\"idx\"\"x\""), sql);
        assertTrue(sql.contains("(\"col\"\"x\" ASC)"), sql);
    }

    @Test
    void requireSnowflakeNameAcceptsLegitValuesAndRejectsInjection() {
        assertEquals("utf8", SnowflakeSqlEscapes.requireSnowflakeName("utf8", "charset"));
        assertEquals("en_US", SnowflakeSqlEscapes.requireSnowflakeName("en_US", "collation"));
        assertThrows(IllegalArgumentException.class,
                () -> SnowflakeSqlEscapes.requireSnowflakeName("utf8'; DROP TABLE t; --", "charset"));
    }

    @Test
    void requireDefaultExpressionAcceptsLiteralsKeywordsAndQuotedStrings() {
        assertEquals("123", SnowflakeSqlEscapes.requireDefaultExpression("123"));
        assertEquals("-1.5", SnowflakeSqlEscapes.requireDefaultExpression(" -1.5 "));
        assertEquals("true", SnowflakeSqlEscapes.requireDefaultExpression("true"));
        assertEquals("CURRENT_TIMESTAMP", SnowflakeSqlEscapes.requireDefaultExpression("CURRENT_TIMESTAMP"));
        assertEquals("CURRENT_TIMESTAMP()", SnowflakeSqlEscapes.requireDefaultExpression("CURRENT_TIMESTAMP()"));
        assertEquals("'abc'", SnowflakeSqlEscapes.requireDefaultExpression("'abc'"));
        assertEquals("'O''Brien'", SnowflakeSqlEscapes.requireDefaultExpression("'O'Brien'"));
    }

    @Test
    void requireDefaultExpressionRejectsInjection() {
        assertThrows(IllegalArgumentException.class,
                () -> SnowflakeSqlEscapes.requireDefaultExpression("1; DROP TABLE t; --"));
        assertThrows(IllegalArgumentException.class,
                () -> SnowflakeSqlEscapes.requireDefaultExpression("(SELECT 1)"));
    }

    @Test
    void requireAscOrDescAcceptsOnlyAscDesc() {
        assertEquals("ASC", SnowflakeSqlEscapes.requireAscOrDesc("asc"));
        assertEquals("DESC", SnowflakeSqlEscapes.requireAscOrDesc("DESC"));
        assertThrows(IllegalArgumentException.class,
                () -> SnowflakeSqlEscapes.requireAscOrDesc("ASC; DROP TABLE t; --"));
    }

    @Test
    void rawNumericDefaultPathIsValidatedInColumnBuilder() {
        TableColumn column = new TableColumn();
        column.setName("amount");
        column.setColumnType("NUMBER");
        column.setDefaultValue("1; DROP TABLE t; --");

        assertThrows(IllegalArgumentException.class,
                () -> SnowflakeColumnTypeEnum.NUMBER.buildCreateColumnSql(column));
    }

    @Test
    void quotedDefaultPathEscapesQuotesInColumnBuilder() {
        TableColumn column = new TableColumn();
        column.setName("name");
        column.setColumnType("VARCHAR");
        column.setDefaultValue("x'; DROP TABLE t; --");

        String sql = SnowflakeColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);

        assertTrue(sql.contains("SET DEFAULT 'x''; DROP TABLE t; --'"), sql);
    }

    private Table tableWithColumn(String columnName, String columnType) {
        TableColumn column = new TableColumn();
        column.setName(columnName);
        column.setColumnType(columnType);

        Table table = new Table();
        table.setName("users");
        table.setColumnList(Collections.singletonList(column));
        table.setIndexList(Collections.emptyList());
        return table;
    }
}
