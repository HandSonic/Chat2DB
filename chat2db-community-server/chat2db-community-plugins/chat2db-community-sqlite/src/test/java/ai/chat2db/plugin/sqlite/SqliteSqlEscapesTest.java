package ai.chat2db.plugin.sqlite;

import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.sqlite.builder.SqliteBuilder;
import ai.chat2db.plugin.sqlite.constant.SqliteMetaDataConstants;
import ai.chat2db.plugin.sqlite.enums.type.SqliteColumnTypeEnum;
import ai.chat2db.plugin.sqlite.enums.type.SqliteIndexTypeEnum;
import ai.chat2db.plugin.sqlite.identifier.SqliteIdentifierProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", SqliteSqlEscapes.escapeSqlLiteral("O'Brien"));
        assertEquals("''", SqliteSqlEscapes.escapeSqlLiteral("'"));
        assertEquals("plain", SqliteSqlEscapes.escapeSqlLiteral("plain"));
        assertEquals("", SqliteSqlEscapes.escapeSqlLiteral(null));
    }

    @Test
    void escapeIdentifierDoublesDoubleQuotesAndStripsWrappingQuotes() {
        assertEquals("WE\"\"IRD", SqliteSqlEscapes.escapeIdentifier("WE\"IRD"));
        assertEquals("ALREADY", SqliteSqlEscapes.escapeIdentifier("\"ALREADY\""));
        assertEquals("", SqliteSqlEscapes.escapeIdentifier(null));
        assertEquals("", SqliteSqlEscapes.escapeIdentifier("\"\""));
        assertEquals("\"A\"\"B\"", SqliteSqlEscapes.quoteIdentifier("A\"B"));
        assertEquals("\"\"\"\"", SqliteSqlEscapes.quoteIdentifier("\""));
    }

    @Test
    void metadataSqlTemplatesNeutralizeLiteralInjection() {
        String payload = "v' OR '1'='1";
        String sql = String.format(SqliteMetaDataConstants.VIEW_DDL_SQL, SqliteSqlEscapes.escapeSqlLiteral(payload));
        assertTrue(sql.contains("name='v'' OR ''1''=''1';"), sql);
        assertFalse(sql.contains("name='v' OR"), sql);
    }

    @Test
    void getMetaDataNameNeutralizesEmbeddedQuotes() {
        SqliteMetaData metaData = new SqliteMetaData();
        String result = metaData.getMetaDataName("main", "A\".\"B");
        assertEquals("\"main\".\"A\"\".\"\"B\"", result);
        assertFalse(result.contains("A\".\"B\"."), "injection payload must not break out of the quoted identifier");
    }

    @Test
    void identifierProcessorDoublesEmbeddedQuotes() {
        SqliteIdentifierProcessor processor = new SqliteIdentifierProcessor();
        assertEquals("plain_name", processor.quoteIdentifier("plain_name"));
        assertEquals("\"a\"\"b\"", processor.quoteIdentifier("a\"b"));
        assertEquals("\"a\"\"; DROP TABLE t; --\"", processor.quoteIdentifier("a\"; DROP TABLE t; --"));
    }

    @Test
    void createTableEscapesNamesAndFlattensComments() {
        Table table = Table.builder()
                .databaseName("main")
                .name("t\"; DROP TABLE u; --")
                .columnList(List.of(TableColumn.builder()
                        .name("c\"d")
                        .columnType("TEXT")
                        .comment("x\n); DROP TABLE u; --")
                        .build()))
                .indexList(List.of())
                .build();

        String sql = new SqliteBuilder().buildCreateTable(table, TableBuilderConfig.defaultConfig());

        assertTrue(sql.contains("\"t\"\"; DROP TABLE u; --\""), sql);
        assertTrue(sql.contains("\"c\"\"d\""), sql);
        assertFalse(sql.contains("x\n"), "comment must not break out of the -- line comment");
    }

    @Test
    void createTableRejectsHostileFreeTextColumnType() {
        Table table = Table.builder()
                .name("t")
                .columnList(List.of(TableColumn.builder()
                        .name("c")
                        .columnType("TEXT); DROP TABLE u; --")
                        .build()))
                .indexList(List.of())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> new SqliteBuilder().buildCreateTable(table, TableBuilderConfig.defaultConfig()));
    }

    @Test
    void alterTableEscapesRenameTarget() {
        Table oldTable = Table.builder().databaseName("main").name("users").columnList(List.of()).indexList(List.of()).build();
        Table newTable = Table.builder().databaseName("main").name("u\"; DROP TABLE t; --").columnList(List.of()).indexList(List.of()).build();

        String sql = new SqliteBuilder().buildAlterTable(oldTable, newTable);

        assertTrue(sql.contains("RENAME TO \"u\"\"; DROP TABLE t; --\""), sql);
    }

    @Test
    void indexScriptQuotesIndexTableAndColumnNames() {
        TableIndex tableIndex = TableIndex.builder()
                .name("i\"x")
                .type("Normal")
                .tableName("t\"y")
                .columnList(List.of(TableIndexColumn.builder().columnName("c\"z").build()))
                .build();

        String sql = SqliteIndexTypeEnum.NORMAL.buildIndexScript(tableIndex);

        assertTrue(sql.contains("INDEX \"i\"\"x\" ON \"t\"\"y\" (\"c\"\"z\")"), sql);
    }

    @Test
    void createColumnSqlQuotesNameAndAcceptsBuiltinCollation() {
        TableColumn column = TableColumn.builder()
                .name("c\"d")
                .columnType("TEXT")
                .collationName("NOCASE")
                .build();

        String sql = SqliteColumnTypeEnum.TEXT.buildCreateColumnSql(column);

        assertTrue(sql.contains("\"c\"\"d\""), sql);
        assertTrue(sql.contains("COLLATE NOCASE"), sql);
    }

    @Test
    void createColumnSqlRejectsHostileCollation() {
        TableColumn column = TableColumn.builder()
                .name("c")
                .columnType("TEXT")
                .collationName("NOCASE; DROP TABLE t; --")
                .build();

        assertThrows(IllegalArgumentException.class, () -> SqliteColumnTypeEnum.TEXT.buildCreateColumnSql(column));
    }

    @Test
    void requireSafeTypeNameAcceptsRealTypesAndRejectsInjection() {
        assertEquals("VARCHAR(255)", SqliteSqlEscapes.requireSafeTypeName("VARCHAR(255)"));
        assertEquals("NUMERIC(10,2)", SqliteSqlEscapes.requireSafeTypeName("NUMERIC(10,2)"));
        assertEquals("DOUBLE PRECISION", SqliteSqlEscapes.requireSafeTypeName("DOUBLE PRECISION"));
        assertThrows(IllegalArgumentException.class,
                () -> SqliteSqlEscapes.requireSafeTypeName("TEXT); DROP TABLE u; --"));
        assertThrows(IllegalArgumentException.class,
                () -> SqliteSqlEscapes.requireSafeTypeName("TEXT\")"));
        assertNull(SqliteSqlEscapes.requireSafeTypeName(null));
    }

    @Test
    void escapeColumnDefaultKeepsQuotedLiteralsAndExpressions() {
        assertEquals("'O''Brien'", SqliteSqlEscapes.escapeColumnDefault("'O''Brien'"));
        assertEquals("''", SqliteSqlEscapes.escapeColumnDefault("''"));
        assertEquals("42", SqliteSqlEscapes.escapeColumnDefault("42"));
        assertEquals("-1.5", SqliteSqlEscapes.escapeColumnDefault("-1.5"));
        assertEquals("CURRENT_TIMESTAMP", SqliteSqlEscapes.escapeColumnDefault("CURRENT_TIMESTAMP"));
        assertEquals("(1+2)", SqliteSqlEscapes.escapeColumnDefault("(1+2)"));
        assertEquals("", SqliteSqlEscapes.escapeColumnDefault(null));
    }

    @Test
    void escapeColumnDefaultNeutralizesAttackStrings() {
        assertEquals("'x''); DROP TABLE u; --'",
                SqliteSqlEscapes.escapeColumnDefault("'x'); DROP TABLE u; --'"));
        assertEquals("'0; DROP TABLE u; --'",
                SqliteSqlEscapes.escapeColumnDefault("0; DROP TABLE u; --"));
    }

    @Test
    void sanitizeLineCommentFlattensLineBreaks() {
        assertEquals("x ); DROP TABLE u; --", SqliteSqlEscapes.sanitizeLineComment("x\n); DROP TABLE u; --"));
        assertEquals("a  b", SqliteSqlEscapes.sanitizeLineComment("a\r\nb"));
        assertEquals("", SqliteSqlEscapes.sanitizeLineComment(null));
    }
}
