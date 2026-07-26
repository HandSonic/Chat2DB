package ai.chat2db.plugin.postgresql;

import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import ai.chat2db.community.domain.api.model.metadata.Database;
import ai.chat2db.community.domain.api.model.metadata.Schema;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.community.domain.api.model.view.ModifyView;
import ai.chat2db.plugin.postgresql.builder.PostgreSQLSqlBuilder;
import ai.chat2db.plugin.postgresql.enums.PostgreSQLViewCheckOptionEnum;
import ai.chat2db.plugin.postgresql.enums.type.PostgreSQLColumnTypeEnum;
import ai.chat2db.plugin.postgresql.enums.type.PostgreSQLIndexTypeEnum;
import ai.chat2db.plugin.postgresql.identifier.PostgreSQLIdentifierProcessor;
import ai.chat2db.plugin.postgresql.value.template.PostgreSQLDmlValueTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSQLIdentifierProcessorTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("a''b", PostgreSQLIdentifierProcessor.INSTANCE.escapeString("a'b"));
        assertEquals("''", PostgreSQLIdentifierProcessor.INSTANCE.escapeString("'"));
        assertEquals("plain", PostgreSQLIdentifierProcessor.INSTANCE.escapeString("plain"));
        // backslash is NOT an escape character under standard_conforming_strings=on
        assertEquals("a\\b", PostgreSQLIdentifierProcessor.INSTANCE.escapeString("a\\b"));
        assertNull(PostgreSQLIdentifierProcessor.INSTANCE.escapeString(null));
    }

    @Test
    void quoteIdentifierDoublesEmbeddedDoubleQuotes() {
        assertEquals("\"plain\"", PostgreSQLIdentifierProcessor.INSTANCE.quoteIdentifier("plain"));
        assertEquals("\"weird\"\"name\"", PostgreSQLIdentifierProcessor.INSTANCE.quoteIdentifier("weird\"name"));
        assertEquals("\"a\"\"; DROP TABLE b; --\"", PostgreSQLIdentifierProcessor.INSTANCE.quoteIdentifier("a\"; DROP TABLE b; --"));
        // one surrounding quote pair is stripped before doubling
        assertEquals("\"a\"\"b\"", PostgreSQLIdentifierProcessor.INSTANCE.quoteIdentifier("\"a\"b\""));
        assertEquals("\"quoted\"", PostgreSQLIdentifierProcessor.INSTANCE.quoteIdentifier("\"quoted\""));
    }

    @Test
    void requirePgNameRejectsInjection() {
        assertEquals("btree", PostgreSqlGuards.requirePgName("btree", "index method"));
        assertEquals("en_US", PostgreSqlGuards.requirePgName("en_US", "role"));
        assertThrows(IllegalArgumentException.class,
                () -> PostgreSqlGuards.requirePgName("btree; DROP TABLE t", "index method"));
        assertThrows(IllegalArgumentException.class,
                () -> PostgreSqlGuards.requirePgName("alice\" ", "schema owner"));
    }

    @Test
    void requireDefaultExpressionAcceptsLegitDefaults() {
        String[] valid = {"0", "-1", "1.5", "+2", "true", "FALSE", "CURRENT_TIMESTAMP", "now",
                "'Y'", "'0'", "'O''Brien'", "'1970-01-01'", "'{}'", "''"};
        for (String value : valid) {
            assertEquals(value, PostgreSqlGuards.requireDefaultExpression(value), "should accept: " + value);
        }
    }

    @Test
    void requireDefaultExpressionRejectsDdlReshapePayloads() {
        String[] payloads = {
                "0) --", "0 --", "1, x INT", "now()", "'abc", "'a' 'b'", "'a'||'b'", "'a'--",
                "'a'; DROP TABLE x--", "0); DROP TABLE t--"
        };
        for (String payload : payloads) {
            assertThrows(IllegalArgumentException.class,
                    () -> PostgreSqlGuards.requireDefaultExpression(payload), "should reject: " + payload);
        }
    }

    @Test
    void requireBitAndHexLiteralsValidateContent() {
        assertEquals("0101", PostgreSqlGuards.requireBitLiteral("0101"));
        assertThrows(IllegalArgumentException.class, () -> PostgreSqlGuards.requireBitLiteral("2"));
        assertThrows(IllegalArgumentException.class, () -> PostgreSqlGuards.requireBitLiteral("1' OR '1'='1"));
        assertEquals("deadBEEF", PostgreSqlGuards.requireHexLiteral("deadBEEF"));
        assertThrows(IllegalArgumentException.class, () -> PostgreSqlGuards.requireHexLiteral("zz'; DROP TABLE t;--"));
    }

    @Test
    void requireEnumConstantRejectsUnknownOption() {
        assertEquals("CASCADED", PostgreSqlGuards.requireEnumConstant(
                "cascaded", PostgreSQLViewCheckOptionEnum.values(), "view check option"));
        assertThrows(IllegalArgumentException.class, () -> PostgreSqlGuards.requireEnumConstant(
                "CASCADED; DROP TABLE t", PostgreSQLViewCheckOptionEnum.values(), "view check option"));
    }

    @Test
    void createTableQuotesNamesAndEscapesComment() {
        PostgreSQLSqlBuilder builder = new PostgreSQLSqlBuilder();
        Table table = Table.builder()
                .schemaName("s\"x")
                .name("a\"; DROP TABLE b; --")
                .columnList(List.of())
                .indexList(List.of())
                .comment("x'; DROP TABLE u;--")
                .build();
        TableBuilderConfig config = TableBuilderConfig.defaultConfig();
        config.setNeedFullTableName(true);

        String sql = builder.buildCreateTable(table, config);

        assertTrue(sql.contains("\"s\"\"x\".\"a\"\"; DROP TABLE b; --\""), sql);
        assertTrue(sql.contains("COMMENT ON TABLE \"a\"\"; DROP TABLE b; --\" IS 'x''; DROP TABLE u;--';"), sql);
    }

    @Test
    void createDatabaseQuotesNameAndEscapesComment() {
        PostgreSQLSqlBuilder builder = new PostgreSQLSqlBuilder();
        Database database = new Database();
        database.setName("db\"x");
        database.setComment("c'd");

        String sql = builder.buildCreateDatabase(database);

        assertTrue(sql.contains("CREATE DATABASE \"db\"\"x\""), sql);
        assertTrue(sql.contains("COMMENT ON DATABASE \"db\"\"x\" IS 'c''d';"), sql);
    }

    @Test
    void createSchemaQuotesNameAndRejectsOwnerInjection() {
        PostgreSQLSqlBuilder builder = new PostgreSQLSqlBuilder();
        Schema benign = new Schema();
        benign.setName("s\"x");
        benign.setOwner("postgres");
        assertTrue(builder.buildCreateSchema(benign).contains("CREATE SCHEMA \"s\"\"x\" AUTHORIZATION postgres"),
                builder.buildCreateSchema(benign));

        Schema malicious = new Schema();
        malicious.setName("s");
        malicious.setOwner("alice; DROP TABLE t");
        assertThrows(IllegalArgumentException.class, () -> builder.buildCreateSchema(malicious));
    }

    @Test
    void createViewRejectsCheckOptionInjectionAndEscapesComment() {
        PostgreSQLSqlBuilder builder = new PostgreSQLSqlBuilder();
        ModifyView malicious = new ModifyView();
        malicious.setViewName("v");
        malicious.setViewBody("select 1");
        malicious.setCheckOption("CASCADED; DROP TABLE t");
        assertThrows(IllegalArgumentException.class, () -> builder.buildCreateView(malicious));

        ModifyView benign = new ModifyView();
        benign.setViewName("v\"x");
        benign.setViewBody("select 1");
        benign.setCheckOption("local");
        benign.setComment("c'd");
        String sql = builder.buildCreateView(benign);
        assertTrue(sql.contains("VIEW \"v\"\"x\""), sql);
        assertTrue(sql.contains("WITH LOCAL CHECK OPTION"), sql);
        assertTrue(sql.contains("is 'c''d';"), sql);
    }

    @Test
    void createColumnSqlQuotesNameAndEscapesStringDefault() {
        TableColumn column = TableColumn.builder()
                .name("a\"b")
                .columnType("VARCHAR")
                .columnSize(255)
                .defaultValue("O'Brien")
                .build();

        String sql = PostgreSQLColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);

        assertTrue(sql.contains("\"a\"\"b\""), sql);
        assertTrue(sql.contains("DEFAULT 'O''Brien'"), sql);
    }

    @Test
    void createColumnSqlRejectsRawDefaultInjection() {
        TableColumn column = TableColumn.builder()
                .name("n")
                .columnType("INT4")
                .defaultValue("0);DROP TABLE t")
                .build();

        assertThrows(IllegalArgumentException.class, () -> PostgreSQLColumnTypeEnum.INT4.buildCreateColumnSql(column));
    }

    @Test
    void columnCommentQuotesNamesAndEscapesComment() {
        TableColumn column = TableColumn.builder()
                .tableName("t\"x")
                .name("c")
                .columnType("TEXT")
                .comment("it's")
                .build();

        String sql = PostgreSQLColumnTypeEnum.TEXT.buildComment(column, PostgreSQLColumnTypeEnum.TEXT);

        assertEquals("COMMENT ON COLUMN \"t\"\"x\".\"c\" IS 'it''s';", sql);
    }

    @Test
    void modifyColumnDeleteQuotesName() {
        TableColumn column = TableColumn.builder()
                .name("a\"b")
                .columnType("TEXT")
                .editStatus("DELETE")
                .build();

        assertEquals("DROP COLUMN \"a\"\"b\"", PostgreSQLColumnTypeEnum.TEXT.buildModifyColumn(column));
    }

    @Test
    void indexScriptQuotesNamesAndValidatesMethod() {
        TableIndex tableIndex = TableIndex.builder()
                .name("i\"x")
                .type("Normal")
                .tableName("t\"b")
                .method("btree")
                .columnList(List.of(TableIndexColumn.builder().columnName("c\"d").build()))
                .build();

        String sql = PostgreSQLIndexTypeEnum.NORMAL.buildIndexScript(tableIndex);

        assertTrue(sql.contains("\"i\"\"x\""), sql);
        assertTrue(sql.contains("ON \"t\"\"b\""), sql);
        assertTrue(sql.contains("USING btree"), sql);
        assertTrue(sql.contains("(\"c\"\"d\")"), sql);

        TableIndex evilMethod = TableIndex.builder()
                .name("i")
                .type("Normal")
                .tableName("t")
                .method("btree; DROP TABLE t")
                .columnList(List.of(TableIndexColumn.builder().columnName("c").build()))
                .build();
        assertThrows(IllegalArgumentException.class, () -> PostgreSQLIndexTypeEnum.NORMAL.buildIndexScript(evilMethod));
    }

    @Test
    void indexCommentAndDropQuoteNamesAndEscapeComment() {
        TableIndex tableIndex = TableIndex.builder()
                .name("i\"x")
                .type("Normal")
                .comment("c'd")
                .build();
        assertEquals("COMMENT ON INDEX \"i\"\"x\" IS 'c''d';",
                PostgreSQLIndexTypeEnum.NORMAL.buildIndexComment(tableIndex));

        TableIndex dropped = TableIndex.builder()
                .name("i")
                .oldName("i\"x")
                .type("Normal")
                .editStatus("DELETE")
                .build();
        assertEquals("DROP INDEX \"i\"\"x\"", PostgreSQLIndexTypeEnum.NORMAL.buildModifyIndex(dropped));
    }

    @Test
    void dmlValueTemplatesEscapeOrValidate() {
        assertEquals("B'0101'", PostgreSQLDmlValueTemplate.wrapBit("0101"));
        assertThrows(IllegalArgumentException.class, () -> PostgreSQLDmlValueTemplate.wrapBit("1' OR '1'='1"));
        assertEquals("E'\\\\xdeadbeef'::bytea", PostgreSQLDmlValueTemplate.wrapBytea("deadbeef"));
        assertThrows(IllegalArgumentException.class, () -> PostgreSQLDmlValueTemplate.wrapBytea("zz'; DROP TABLE t;--"));
        assertEquals("'{\"a\":\"b\"}'::json", PostgreSQLDmlValueTemplate.wrapJson("{\"a\":\"b\"}"));
        assertEquals("'x''y'::jsonb", PostgreSQLDmlValueTemplate.wrapJsonb("x'y"));
    }
}
