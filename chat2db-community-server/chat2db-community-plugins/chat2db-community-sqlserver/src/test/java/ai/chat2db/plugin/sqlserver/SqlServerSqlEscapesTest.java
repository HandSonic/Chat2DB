package ai.chat2db.plugin.sqlserver;

import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import ai.chat2db.community.domain.api.model.metadata.Database;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.community.domain.api.model.view.ModifyView;
import ai.chat2db.plugin.sqlserver.builder.SqlServerSqlBuilder;
import ai.chat2db.plugin.sqlserver.constant.SQLConstant;
import ai.chat2db.plugin.sqlserver.enums.type.SqlServerColumnTypeEnum;
import ai.chat2db.plugin.sqlserver.enums.type.SqlServerIndexTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static ai.chat2db.plugin.sqlserver.identifier.SqlServerIdentifierUtils.escapeIdentifier;
import static ai.chat2db.plugin.sqlserver.identifier.SqlServerIdentifierUtils.escapeStringLiteral;
import static ai.chat2db.plugin.sqlserver.identifier.SqlServerIdentifierUtils.quoteIdentifierPart;
import static ai.chat2db.plugin.sqlserver.identifier.SqlServerIdentifierUtils.validateCollation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerSqlEscapesTest {

    @Test
    void shouldDoubleSingleQuotesInStringLiterals() {
        assertEquals("O''Brien", escapeStringLiteral("O'Brien"));
        assertEquals("", escapeStringLiteral(null));
        assertEquals("plain", escapeStringLiteral("plain"));
    }

    @Test
    void shouldDoubleClosingBracketsInQuotedIdentifiers() {
        assertEquals("[weird]]name]", quoteIdentifierPart("weird]name"));
        assertEquals("a]]b", escapeIdentifier("a]b"));
        assertEquals("", escapeIdentifier(null));
    }

    @Test
    void shouldNeutralizeMaliciousCommentAndNamesInCreateTable() {
        SqlServerSqlBuilder builder = new SqlServerSqlBuilder();
        Table table = new Table();
        table.setSchemaName("dbo");
        table.setName("users];DROP TABLE t;--");
        table.setComment("x'; DROP TABLE users;--");
        TableColumn column = new TableColumn();
        column.setName("id");
        column.setColumnType("INT");
        column.setNullable(1);
        table.setColumnList(List.of(column));
        table.setIndexList(List.of());

        String script = builder.buildCreateTable(table, TableBuilderConfig.defaultConfig());

        assertTrue(script.contains("CREATE TABLE [dbo].[users]];DROP TABLE t;--] ("), script);
        assertTrue(script.contains("'x''; DROP TABLE users;--'"), script);
        assertFalse(script.contains("'x'; DROP"), script);
    }

    @Test
    void shouldNeutralizeMaliciousNamesInIndexScriptAndComment() {
        TableIndex tableIndex = new TableIndex();
        tableIndex.setSchemaName("s];DROP");
        tableIndex.setTableName("t");
        tableIndex.setName("ix");
        tableIndex.setType("NONCLUSTERED");
        tableIndex.setComment("c';DROP--");
        TableIndexColumn indexColumn = new TableIndexColumn();
        indexColumn.setColumnName("id");
        indexColumn.setAscOrDesc("ASC");
        tableIndex.setColumnList(List.of(indexColumn));

        String script = SqlServerIndexTypeEnum.NONCLUSTERED.buildIndexScript(tableIndex);
        assertTrue(script.contains("ON [s]];DROP].[t]"), script);

        SqlServerSqlBuilder builder = new SqlServerSqlBuilder();
        Table table = new Table();
        table.setSchemaName("dbo");
        table.setName("t");
        TableColumn column = new TableColumn();
        column.setName("id");
        column.setColumnType("INT");
        column.setNullable(1);
        table.setColumnList(List.of(column));
        table.setIndexList(new ArrayList<>(List.of(tableIndex)));

        String createScript = builder.buildCreateTable(table, TableBuilderConfig.defaultConfig());
        assertTrue(createScript.contains("'c'';DROP--'"), createScript);
        assertFalse(createScript.contains("'c';DROP--'"), createScript);
    }

    @Test
    void shouldRejectInvalidIndexColumnSortOrder() {
        TableIndex tableIndex = new TableIndex();
        tableIndex.setSchemaName("dbo");
        tableIndex.setTableName("t");
        tableIndex.setName("ix");
        tableIndex.setType("NONCLUSTERED");
        TableIndexColumn indexColumn = new TableIndexColumn();
        indexColumn.setColumnName("id");
        indexColumn.setAscOrDesc("ASC; DROP TABLE t;--");
        tableIndex.setColumnList(List.of(indexColumn));

        assertThrows(IllegalArgumentException.class,
                () -> SqlServerIndexTypeEnum.NONCLUSTERED.buildIndexScript(tableIndex));
    }

    @Test
    void shouldEscapeDatabaseNameAndCommentInCreateDatabase() {
        SqlServerSqlBuilder builder = new SqlServerSqlBuilder();
        Database database = new Database();
        database.setName("db];DROP");
        database.setCollation("SQL_Latin1_General_CP1_CI_AS");
        database.setComment("it's");

        String script = builder.buildCreateDatabase(database);

        assertTrue(script.contains("CREATE DATABASE [db]];DROP]"), script);
        assertTrue(script.contains("COLLATE SQL_Latin1_General_CP1_CI_AS"), script);
        assertTrue(script.contains("exec [db]];DROP].sys."), script);
        assertTrue(script.contains("'it''s'"), script);
    }

    @Test
    void shouldAcceptLegitCollationAndRejectInjection() {
        assertEquals("Latin1_General_100_CI_AS_KS_WS_SC", validateCollation("Latin1_General_100_CI_AS_KS_WS_SC"));

        SqlServerSqlBuilder builder = new SqlServerSqlBuilder();
        Database database = new Database();
        database.setName("db");
        database.setCollation("Latin1; DROP TABLE t;--");
        assertThrows(IllegalArgumentException.class, () -> builder.buildCreateDatabase(database));
    }

    @Test
    void shouldKeepQuotedStringAndExpressionDefaultsUnchanged() {
        TableColumn quotedDefault = new TableColumn();
        quotedDefault.setName("c");
        quotedDefault.setColumnType("VARCHAR");
        quotedDefault.setColumnSize(50);
        quotedDefault.setNullable(1);
        quotedDefault.setDefaultValue("'O''Brien'");
        assertTrue(SqlServerColumnTypeEnum.VARCHAR.buildCreateColumnSql(quotedDefault)
                .contains("DEFAULT 'O''Brien'"));

        TableColumn expressionDefault = new TableColumn();
        expressionDefault.setName("d");
        expressionDefault.setColumnType("DATETIME2");
        expressionDefault.setNullable(1);
        expressionDefault.setDefaultValue("(getdate())");
        assertTrue(SqlServerColumnTypeEnum.DATETIME2.buildCreateColumnSql(expressionDefault)
                .contains("DEFAULT (getdate())"));
    }

    @Test
    void shouldWhitelistViewAttributes() {
        SqlServerSqlBuilder builder = new SqlServerSqlBuilder();
        ModifyView view = new ModifyView();
        view.setSchemaName("dbo");
        view.setViewName("v");
        view.setViewBody("SELECT 1");
        view.setViewAttributes(List.of("SCHEMABINDING"));
        assertTrue(builder.buildCreateView(view).contains("WITH SCHEMABINDING"));

        ModifyView malicious = new ModifyView();
        malicious.setSchemaName("dbo");
        malicious.setViewName("v");
        malicious.setViewBody("SELECT 1");
        malicious.setViewAttributes(List.of("SCHEMABINDING OPTION(RECOMPILE); DROP TABLE t;--"));
        assertThrows(IllegalArgumentException.class, () -> builder.buildCreateView(malicious));
    }

    @Test
    void shouldEscapeNamesInMetadataCommentBuilders() {
        String script = SQLConstant.buildTableComment("c", "s'x", "t");
        assertTrue(script.contains("N's''x'"), script);

        String indexScript = SQLConstant.buildIndexComment("c", "dbo", "t", "i'x");
        assertTrue(indexScript.contains("N'i''x'"), indexScript);
    }

    @Test
    void shouldRequoteAndEscapeTableNames() {
        ExposedBuilder builder = new ExposedBuilder();
        assertEquals("[db].[dbo].[users]", builder.tableName("db", "dbo", "users"));
        assertEquals("[db].[dbo].[users]", builder.tableName("db", "dbo", "[users]"));
        assertEquals("[us]]ers]", builder.tableName(null, null, "us]ers"));
        assertEquals("[us]]ers]", builder.tableName(null, null, "[us]]ers]"));
    }

    private static final class ExposedBuilder extends SqlServerSqlBuilder {
        private String tableName(String databaseName, String schemaName, String tableName) {
            StringBuilder script = new StringBuilder();
            buildTableName(databaseName, schemaName, tableName, script);
            return script.toString();
        }
    }
}
