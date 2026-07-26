package ai.chat2db.plugin.hive;

import ai.chat2db.community.domain.api.model.metadata.Database;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.hive.builder.HiveSqlBuilder;
import ai.chat2db.plugin.hive.enums.type.HiveColumnTypeEnum;
import ai.chat2db.plugin.hive.enums.type.HiveIndexTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesBackslashBeforeSingleQuote() {
        assertEquals("a''b\\\\c", HiveSqlEscapes.escapeSqlLiteral("a'b\\c"));
        assertEquals("''", HiveSqlEscapes.escapeSqlLiteral("'"));
        assertEquals("plain", HiveSqlEscapes.escapeSqlLiteral("plain"));
        assertNull(HiveSqlEscapes.escapeSqlLiteral(null));
    }

    @Test
    void quoteIdentifierDoublesEmbeddedBackticks() {
        assertEquals("`plain`", HiveSqlEscapes.quoteIdentifier("plain"));
        assertEquals("`weird``name`", HiveSqlEscapes.quoteIdentifier("weird`name"));
        assertEquals("`a``; DROP TABLE b; --`", HiveSqlEscapes.quoteIdentifier("a`; DROP TABLE b; --"));
        // one surrounding backtick pair is stripped before doubling
        assertEquals("`a``b`", HiveSqlEscapes.quoteIdentifier("`a`b`"));
        assertEquals("`quoted`", HiveSqlEscapes.quoteIdentifier("`quoted`"));
    }

    @Test
    void requireHiveNameRejectsInjection() {
        assertEquals("utf8", HiveSqlEscapes.requireHiveName("utf8", "charset"));
        assertThrows(IllegalArgumentException.class,
                () -> HiveSqlEscapes.requireHiveName("InnoDB, COMMENT='x'", "engine"));
        assertThrows(IllegalArgumentException.class,
                () -> HiveSqlEscapes.requireHiveName("utf8;DROP TABLE t", "charset"));
    }

    @Test
    void requireNumericDefaultRejectsNonLiteral() {
        assertEquals("42", HiveSqlEscapes.requireNumericDefault("42"));
        assertEquals("-1.5", HiveSqlEscapes.requireNumericDefault("-1.5"));
        assertEquals("1e3", HiveSqlEscapes.requireNumericDefault("1e3"));
        assertEquals("TRUE", HiveSqlEscapes.requireNumericDefault("TRUE"));
        assertThrows(IllegalArgumentException.class, () -> HiveSqlEscapes.requireNumericDefault("0);DROP TABLE t"));
        assertThrows(IllegalArgumentException.class, () -> HiveSqlEscapes.requireNumericDefault("(uuid())"));
    }

    @Test
    void requireAscOrDescRejectsInjection() {
        assertEquals("ASC", HiveSqlEscapes.requireAscOrDesc("asc"));
        assertEquals("DESC", HiveSqlEscapes.requireAscOrDesc("DESC"));
        assertThrows(IllegalArgumentException.class,
                () -> HiveSqlEscapes.requireAscOrDesc("DESC, `x` ASC; DROP TABLE t"));
    }

    @Test
    void createColumnSqlQuotesNameAndEscapesComment() {
        TableColumn column = TableColumn.builder()
                .name("a`; DROP TABLE b; --")
                .columnType("VARCHAR")
                .columnSize(255)
                .comment("it's")
                .build();

        String sql = HiveColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);

        assertTrue(sql.contains("`a``; DROP TABLE b; --`"), sql);
        assertTrue(sql.contains("COMMENT 'it''s'"), sql);
    }

    @Test
    void modifyColumnQuotesOldAndNewNames() {
        TableColumn column = TableColumn.builder()
                .name("n`b")
                .oldName("o`; DROP TABLE b; --")
                .columnType("INT")
                .editStatus("MODIFY")
                .build();

        String sql = HiveColumnTypeEnum.INT.buildModifyColumn(column);

        assertTrue(sql.startsWith("CHANGE COLUMN `o``; DROP TABLE b; --` `n``b`"), sql);
    }

    @Test
    void dropColumnQuotesName() {
        TableColumn column = TableColumn.builder()
                .name("a`; DROP TABLE b; --")
                .columnType("INT")
                .editStatus("DELETE")
                .build();

        assertEquals("DROP COLUMN `a``; DROP TABLE b; --`", HiveColumnTypeEnum.INT.buildModifyColumn(column));
    }

    @Test
    void createTableQuotesNamesAndEscapesComment() {
        Table table = Table.builder()
                .name("a`; DROP TABLE b; --")
                .databaseName("d`b")
                .comment("it's")
                .columnList(List.of())
                .indexList(List.of())
                .build();

        String sql = new HiveSqlBuilder().buildCreateTable(table, null);

        assertTrue(sql.contains("`d``b`.`a``; DROP TABLE b; --`"), sql);
        assertTrue(sql.contains("COMMENT 'it''s'"), sql);
    }

    @Test
    void createTableRejectsMaliciousEngine() {
        Table table = Table.builder()
                .name("t")
                .engine("InnoDB, COMMENT='x'")
                .columnList(List.of())
                .indexList(List.of())
                .build();

        assertThrows(IllegalArgumentException.class, () -> new HiveSqlBuilder().buildCreateTable(table, null));
    }

    @Test
    void alterTableRenameQuotesNewNameAndEscapesComment() {
        Table oldTable = Table.builder().name("t1").columnList(List.of()).indexList(List.of()).build();
        Table newTable = Table.builder()
                .name("x`; DROP TABLE b; --")
                .comment("it's")
                .columnList(List.of())
                .indexList(List.of())
                .build();

        String sql = new HiveSqlBuilder().buildAlterTable(oldTable, newTable);

        assertTrue(sql.contains("RENAME TO `x``; DROP TABLE b; --`"), sql);
        assertTrue(sql.contains("'comment' = 'it''s'"), sql);
    }

    @Test
    void createDatabaseQuotesNameAndEscapesComment() {
        Database database = Database.builder()
                .name("a`; DROP TABLE b; --")
                .comment("it's")
                .build();

        String sql = new HiveSqlBuilder().buildCreateDatabase(database);

        assertTrue(sql.startsWith("CREATE DATABASE `a``; DROP TABLE b; --`"), sql);
        assertTrue(sql.contains("COMMENT 'it''s'"), sql);
    }

    @Test
    void indexSqlQuotesNamesAndEscapesComment() {
        TableIndex index = TableIndex.builder()
                .name("i`; DROP TABLE b; --")
                .type("Normal")
                .comment("it's")
                .columnList(List.of(TableIndexColumn.builder().columnName("c`y").ascOrDesc("asc").build()))
                .build();

        String sql = HiveIndexTypeEnum.NORMAL.buildIndexScript(index);

        assertTrue(sql.contains("`i``; DROP TABLE b; --`"), sql);
        assertTrue(sql.contains("(`c``y` ASC)"), sql);
        assertTrue(sql.contains("COMMENT 'it''s'"), sql);
    }

    @Test
    void dropIndexQuotesOldName() {
        TableIndex index = TableIndex.builder()
                .oldName("a`; DROP TABLE b; --")
                .type("Normal")
                .editStatus("DELETE")
                .build();

        assertEquals("DROP INDEX `a``; DROP TABLE b; --`", HiveIndexTypeEnum.NORMAL.buildModifyIndex(index));
    }

    @Test
    void dropTableQuotesIdentifier() {
        HiveDBManager manager = new HiveDBManager();
        assertEquals("drop table if exists `a``; DROP TABLE b; --`",
                manager.dropTable(null, null, null, "a`; DROP TABLE b; --"));
    }

    @Test
    void metaDataFormatAndNameQuoteIdentifiers() {
        assertEquals("`a``b`", HiveMetaData.format("a`b"));
        assertEquals("`a``; DROP TABLE b; --`", HiveMetaData.format("a`; DROP TABLE b; --"));
        assertEquals("`db`.`a``b`", new HiveMetaData().getMetaDataName("ignored", "db", "a`b"));
    }
}
