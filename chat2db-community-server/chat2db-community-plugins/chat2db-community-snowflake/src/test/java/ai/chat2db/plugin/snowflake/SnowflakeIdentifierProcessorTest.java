package ai.chat2db.plugin.snowflake;

import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.snowflake.builder.SnowflakeSqlBuilder;
import ai.chat2db.plugin.snowflake.enums.type.SnowflakeColumnTypeEnum;
import ai.chat2db.plugin.snowflake.enums.type.SnowflakeIndexTypeEnum;
import ai.chat2db.plugin.snowflake.identifier.SnowflakeIdentifierProcessor;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdentifierProcessorTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", SnowflakeIdentifierProcessor.INSTANCE.escapeString("O'Brien"));
        assertEquals("plain", SnowflakeIdentifierProcessor.INSTANCE.escapeString("plain"));
    }

    @Test
    void escapeIdentifierDoublesDoubleQuotes() {
        assertEquals("we\"\"ird", SnowflakeIdentifierProcessor.escapeIdentifier("we\"ird"));
    }

    @Test
    void quoteIdentifierReturnsValidPlainIdentifiersUnquoted() {
        assertEquals("users", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier("users"));
        assertEquals("T_1", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier("T_1"));
        assertEquals("users", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier("users", null, null));
    }

    @Test
    void quoteIdentifierPassesThroughNullAndBlank() {
        assertNull(SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier(null));
        assertEquals("", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier(""));
        assertEquals(" ", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier(" "));
        assertNull(SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier(null, 1, 0));
    }

    @Test
    void quoteIdentifierQuotesIdentifiersNeedingQuotes() {
        assertEquals("\"we\"\"ird\"", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier("we\"ird"));
        assertEquals("\"ta\"\"ble\"", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier("\"ta\"ble\""));
        assertEquals("\"has space\"", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier("has space"));
        assertEquals("\"1st\"", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifier("1st"));
    }

    @Test
    void quoteIdentifierIgnoreCaseAlwaysQuotes() {
        assertEquals("\"users\"", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifierIgnoreCase("users"));
        assertNull(SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifierIgnoreCase(null));
    }

    @Test
    void quoteIdentifierAlwaysStripsOnePairThenDoublesEmbeddedQuotes() {
        assertEquals("\"users\"", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifierAlways("users"));
        assertEquals("\"we\"\"ird\"", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifierAlways("we\"ird"));
        assertEquals("\"ta\"\"ble\"", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifierAlways("\"ta\"ble\""));
    }

    @Test
    void quoteIdentifierAlwaysPassesThroughNullAndBlank() {
        assertNull(SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifierAlways(null));
        assertEquals("", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifierAlways(""));
        assertEquals(" ", SnowflakeIdentifierProcessor.INSTANCE.quoteIdentifierAlways(" "));
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
        assertEquals("utf8", SnowflakeSqlGuards.requireSnowflakeName("utf8", "charset"));
        assertEquals("en_US", SnowflakeSqlGuards.requireSnowflakeName("en_US", "collation"));
        assertThrows(IllegalArgumentException.class,
                () -> SnowflakeSqlGuards.requireSnowflakeName("utf8'; DROP TABLE t; --", "charset"));
    }

    @Test
    void requireDefaultExpressionAcceptsLiteralsKeywordsAndQuotedStrings() {
        assertEquals("123", SnowflakeSqlGuards.requireDefaultExpression("123"));
        assertEquals("-1.5", SnowflakeSqlGuards.requireDefaultExpression(" -1.5 "));
        assertEquals("true", SnowflakeSqlGuards.requireDefaultExpression("true"));
        assertEquals("CURRENT_TIMESTAMP", SnowflakeSqlGuards.requireDefaultExpression("CURRENT_TIMESTAMP"));
        assertEquals("CURRENT_TIMESTAMP()", SnowflakeSqlGuards.requireDefaultExpression("CURRENT_TIMESTAMP()"));
        assertEquals("'abc'", SnowflakeSqlGuards.requireDefaultExpression("'abc'"));
        assertEquals("'O''Brien'", SnowflakeSqlGuards.requireDefaultExpression("'O'Brien'"));
    }

    @Test
    void requireDefaultExpressionRejectsInjection() {
        assertThrows(IllegalArgumentException.class,
                () -> SnowflakeSqlGuards.requireDefaultExpression("1; DROP TABLE t; --"));
        assertThrows(IllegalArgumentException.class,
                () -> SnowflakeSqlGuards.requireDefaultExpression("(SELECT 1)"));
    }

    @Test
    void requireAscOrDescAcceptsOnlyAscDesc() {
        assertEquals("ASC", SnowflakeSqlGuards.requireAscOrDesc("asc"));
        assertEquals("DESC", SnowflakeSqlGuards.requireAscOrDesc("DESC"));
        assertThrows(IllegalArgumentException.class,
                () -> SnowflakeSqlGuards.requireAscOrDesc("ASC; DROP TABLE t; --"));
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
