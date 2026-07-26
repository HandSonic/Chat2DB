package ai.chat2db.plugin.mysql;

import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.community.domain.api.model.view.ModifyView;
import ai.chat2db.plugin.mysql.builder.MysqlSqlBuilder;
import ai.chat2db.plugin.mysql.enums.MysqlViewAlgorithmOptionEnum;
import ai.chat2db.plugin.mysql.enums.type.MysqlColumnTypeEnum;
import ai.chat2db.plugin.mysql.enums.type.MysqlIndexTypeEnum;
import ai.chat2db.plugin.mysql.value.template.MysqlDmlValueTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.chat2db.plugin.mysql.constant.MysqlSqlConstants.SQL_DROP_VIEW_TEMPLATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesBackslashBeforeSingleQuote() {
        assertEquals("a''b\\\\c", MysqlSqlEscapes.escapeSqlLiteral("a'b\\c"));
        assertEquals("''", MysqlSqlEscapes.escapeSqlLiteral("'"));
        assertEquals("plain", MysqlSqlEscapes.escapeSqlLiteral("plain"));
        assertNull(MysqlSqlEscapes.escapeSqlLiteral(null));
    }

    @Test
    void quoteIdentifierDoublesEmbeddedBackticks() {
        assertEquals("`plain`", MysqlSqlEscapes.quoteIdentifier("plain"));
        assertEquals("`weird``name`", MysqlSqlEscapes.quoteIdentifier("weird`name"));
        assertEquals("`a``; DROP TABLE b; --`", MysqlSqlEscapes.quoteIdentifier("a`; DROP TABLE b; --"));
        // one surrounding backtick pair is stripped before doubling
        assertEquals("`a``b`", MysqlSqlEscapes.quoteIdentifier("`a`b`"));
        assertEquals("`quoted`", MysqlSqlEscapes.quoteIdentifier("`quoted`"));
    }

    @Test
    void requireMysqlNameRejectsInjection() {
        assertEquals("utf8mb4_0900_ai_ci", MysqlSqlEscapes.requireMysqlName("utf8mb4_0900_ai_ci", "collation"));
        assertThrows(IllegalArgumentException.class,
                () -> MysqlSqlEscapes.requireMysqlName("InnoDB, COMMENT='x'", "engine"));
        assertThrows(IllegalArgumentException.class,
                () -> MysqlSqlEscapes.requireMysqlName("utf8mb4;DROP TABLE t", "charset"));
    }

    @Test
    void requireNumericDefaultRejectsNonLiteral() {
        assertEquals("42", MysqlSqlEscapes.requireNumericDefault("42"));
        assertEquals("-1.5", MysqlSqlEscapes.requireNumericDefault("-1.5"));
        assertEquals("1e3", MysqlSqlEscapes.requireNumericDefault("1e3"));
        assertThrows(IllegalArgumentException.class, () -> MysqlSqlEscapes.requireNumericDefault("0);DROP TABLE t"));
        assertThrows(IllegalArgumentException.class, () -> MysqlSqlEscapes.requireNumericDefault("(uuid())"));
    }

    @Test
    void requireBitLiteralRejectsNonBits() {
        assertEquals("0101", MysqlSqlEscapes.requireBitLiteral("0101"));
        assertThrows(IllegalArgumentException.class, () -> MysqlSqlEscapes.requireBitLiteral("2"));
        assertThrows(IllegalArgumentException.class, () -> MysqlSqlEscapes.requireBitLiteral("1' OR '1'='1"));
    }

    @Test
    void requireDefinerAcceptsOnlyAccountSyntax() {
        assertEquals("root@localhost", MysqlSqlEscapes.requireDefiner("root@localhost"));
        assertEquals("'root'@'%'", MysqlSqlEscapes.requireDefiner("'root'@'%'"));
        assertEquals("`root`@`localhost`", MysqlSqlEscapes.requireDefiner("`root`@`localhost`"));
        assertThrows(IllegalArgumentException.class,
                () -> MysqlSqlEscapes.requireDefiner("root@localhost SQL SECURITY INVOKER"));
    }

    @Test
    void requireEnumConstantRejectsUnknownOption() {
        assertEquals("MERGE",
                MysqlSqlEscapes.requireEnumConstant("merge", MysqlViewAlgorithmOptionEnum.values(), "algorithm"));
        assertThrows(IllegalArgumentException.class, () -> MysqlSqlEscapes.requireEnumConstant(
                "MERGE SQL SECURITY INVOKER", MysqlViewAlgorithmOptionEnum.values(), "algorithm"));
    }

    @Test
    void quoteEnumValuesKeepsBenignListAndNeutralizesMaliciousList() {
        assertEquals("'draft','published'", MysqlSqlEscapes.quoteEnumValues("'draft','published'"));
        assertEquals("('draft','published')", MysqlSqlEscapes.quoteEnumValues("('draft','published')"));
        // unbalanced quote: whole input is re-escaped into a single inert string literal
        assertEquals("'''),DROP TABLE t;-- x'", MysqlSqlEscapes.quoteEnumValues("'),DROP TABLE t;-- x"));
        // balanced items stay split; hostile content stays inside a re-escaped literal
        assertEquals("'a','b''); DROP TABLE t;-- '", MysqlSqlEscapes.quoteEnumValues("'a','b'); DROP TABLE t;-- '"));
    }

    @Test
    void createColumnSqlQuotesColumnNameAndEscapesComment() {
        TableColumn column = TableColumn.builder()
                .name("a`b")
                .columnType("VARCHAR")
                .columnSize(255)
                .comment("it's")
                .build();

        String sql = MysqlColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);

        assertTrue(sql.contains("`a``b`"), sql);
        assertTrue(sql.contains("COMMENT 'it''s'"), sql);
    }

    @Test
    void createEnumColumnSqlReEscapesValueList() {
        TableColumn column = TableColumn.builder()
                .name("e")
                .columnType("ENUM")
                .value("'),DROP TABLE t;-- x")
                .build();

        String sql = MysqlColumnTypeEnum.ENUM.buildCreateColumnSql(column);

        assertTrue(sql.contains("ENUM('''),DROP TABLE t;-- x')"), sql);
    }

    @Test
    void createColumnSqlRejectsRawDefaultInjection() {
        TableColumn column = TableColumn.builder()
                .name("n")
                .columnType("INT")
                .defaultValue("0);DROP TABLE t")
                .build();

        assertThrows(IllegalArgumentException.class, () -> MysqlColumnTypeEnum.INT.buildCreateColumnSql(column));
    }

    @Test
    void indexScriptQuotesIndexNameAndEscapesComment() {
        TableIndex tableIndex = TableIndex.builder()
                .name("i`x")
                .type("Normal")
                .method("BTREE")
                .comment("c'd")
                .columnList(List.of(TableIndexColumn.builder().columnName("col`1").build()))
                .build();

        String sql = MysqlIndexTypeEnum.NORMAL.buildIndexScript(tableIndex);

        assertTrue(sql.contains("`i``x`"), sql);
        assertTrue(sql.contains("(`col``1`)"), sql);
        assertTrue(sql.contains("COMMENT 'c''d'"), sql);
    }

    @Test
    void indexScriptCanonicalizesAscOrDescAndRejectsInjection() {
        TableIndex benign = TableIndex.builder()
                .name("i")
                .type("Normal")
                .method("BTREE")
                .columnList(List.of(TableIndexColumn.builder().columnName("c").ascOrDesc("desc").build()))
                .build();
        assertTrue(MysqlIndexTypeEnum.NORMAL.buildIndexScript(benign).contains("(`c` DESC)"),
                MysqlIndexTypeEnum.NORMAL.buildIndexScript(benign));

        TableIndex malicious = TableIndex.builder()
                .name("i")
                .type("Normal")
                .method("BTREE")
                .columnList(List.of(TableIndexColumn.builder().columnName("c")
                        .ascOrDesc("DESC, DROP TABLE t;--").build()))
                .build();
        assertThrows(IllegalArgumentException.class, () -> MysqlIndexTypeEnum.NORMAL.buildIndexScript(malicious));
    }

    @Test
    void createTableEscapesCommentAndRejectsEngineInjection() {
        MysqlSqlBuilder builder = new MysqlSqlBuilder();
        Table table = Table.builder()
                .name("t")
                .columnList(List.of())
                .indexList(List.of())
                .comment("x'; DROP TABLE u;--")
                .build();

        String sql = builder.ddl().table().buildCreateTable(table, TableBuilderConfig.defaultConfig());

        assertTrue(sql.contains("COMMENT='x''; DROP TABLE u;--'"), sql);

        Table evilEngine = Table.builder()
                .name("t")
                .columnList(List.of())
                .indexList(List.of())
                .engine("InnoDB COMMENT='x'")
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> builder.ddl().table().buildCreateTable(evilEngine, TableBuilderConfig.defaultConfig()));
    }

    @Test
    void createViewRejectsCheckOptionAndDefinerInjection() {
        MysqlSqlBuilder builder = new MysqlSqlBuilder();
        ModifyView modifyView = new ModifyView();
        modifyView.setViewName("v");
        modifyView.setViewBody("select 1");
        modifyView.setCheckOption("CASCADED; DROP TABLE t");
        assertThrows(IllegalArgumentException.class, () -> builder.buildCreateView(modifyView));

        ModifyView definerView = new ModifyView();
        definerView.setViewName("v");
        definerView.setViewBody("select 1");
        definerView.setDefiner("root@localhost SQL SECURITY INVOKER");
        assertThrows(IllegalArgumentException.class, () -> builder.buildCreateView(definerView));
    }

    @Test
    void dropTableAndDropViewQuoteIdentifiers() {
        MysqlDBManager manager = new MysqlDBManager();
        assertEquals("DROP TABLE `a``;DROP TABLE b;--`",
                manager.dropTable(null, null, null, "a`;DROP TABLE b;--"));
        assertEquals("DROP VIEW `db`.`v`",
                String.format(SQL_DROP_VIEW_TEMPLATE, MysqlDBManager.format("db"), MysqlDBManager.format("v")));
    }

    @Test
    void dmlValueTemplatesEscapeOrValidate() {
        assertEquals("ST_GeomFromText('POINT(1 1)')", MysqlDmlValueTemplate.wrapGeometry("POINT(1 1)"));
        assertEquals("ST_GeomFromText('x''y')", MysqlDmlValueTemplate.wrapGeometry("x'y"));
        assertEquals("b'0101'", MysqlDmlValueTemplate.wrapBit("0101"));
        assertThrows(IllegalArgumentException.class, () -> MysqlDmlValueTemplate.wrapBit("1' OR '1'='1"));
    }
}
