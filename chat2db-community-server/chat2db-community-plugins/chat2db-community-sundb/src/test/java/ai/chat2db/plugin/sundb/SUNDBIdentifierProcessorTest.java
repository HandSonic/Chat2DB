package ai.chat2db.plugin.sundb;

import ai.chat2db.community.domain.api.model.metadata.Schema;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.sundb.builder.SUNDBSqlBuilder;
import ai.chat2db.plugin.sundb.enums.type.SUNDBColumnTypeEnum;
import ai.chat2db.plugin.sundb.enums.type.SUNDBIndexTypeEnum;
import ai.chat2db.plugin.sundb.identifier.SUNDBIdentifierProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the SQL-injection hardening helpers (#1914): single-quote doubling
 * for string literals, double-quote doubling for quoted identifiers, and
 * representative SQL-building paths fed with names containing quote chars.
 */
class SUNDBIdentifierProcessorTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", SUNDBIdentifierProcessor.INSTANCE.escapeString("O'Brien"));
        assertEquals("a''b''c", SUNDBIdentifierProcessor.INSTANCE.escapeString("a'b'c"));
        assertEquals("plain", SUNDBIdentifierProcessor.INSTANCE.escapeString("plain"));
        assertEquals("", SUNDBIdentifierProcessor.INSTANCE.escapeString(null));
    }

    @Test
    void escapeIdentifierDoublesEmbeddedDoubleQuotesAndStripsOuterQuotes() {
        assertEquals("we\"\"ird", SUNDBIdentifierProcessor.escapeIdentifier("we\"ird"));
        assertEquals("abc", SUNDBIdentifierProcessor.escapeIdentifier("\"abc\""));
        assertEquals("a\"\"b", SUNDBIdentifierProcessor.escapeIdentifier("\"a\"b\""));
        assertEquals("", SUNDBIdentifierProcessor.escapeIdentifier(null));
        assertEquals("\"we\"\"ird\"", SUNDBIdentifierProcessor.INSTANCE.quoteIdentifier("we\"ird"));
    }

    @Test
    void getMetaDataNameNeutralizesQuotesInNames() {
        String name = new SUNDBMetaData().getMetaDataName("sch\"ema", "ta\"ble");

        assertEquals("\"sch\"\"ema\".\"ta\"\"ble\"", name);
        assertFalse(name.contains("sch\"ema"));
    }

    @Test
    void buildCreateSchemaQuotesAndEscapesNameAndOwner() {
        Schema schema = new Schema();
        schema.setName("we\"ird");
        schema.setOwner("ad\"min");

        String sql = new SUNDBSqlBuilder().buildCreateSchema(schema);

        assertEquals("CREATE SCHEMA \"we\"\"ird\" AUTHORIZATION \"ad\"\"min\"", sql);
        assertFalse(sql.contains("we\"ird"));
    }

    @Test
    void dropTableEscapesSchemaAndTableIdentifiers() {
        String sql = new SUNDBDBManager().dropTable(null, "db", "sch\"ema", "ta\"ble");

        assertEquals("DROP TABLE IF EXISTS \"sch\"\"ema\".\"ta\"\"ble\"", sql);
        assertFalse(sql.contains("sch\"ema"));
    }

    @Test
    void buildIndexScriptEscapesSchemaTableIndexAndColumnNames() {
        TableIndex tableIndex = new TableIndex();
        tableIndex.setSchemaName("sch\"ema");
        tableIndex.setTableName("ta\"ble");
        tableIndex.setName("idx\"name");
        TableIndexColumn column = new TableIndexColumn();
        column.setColumnName("co\"l");
        tableIndex.setColumnList(List.of(column));

        String sql = SUNDBIndexTypeEnum.NORMAL.buildIndexScript(tableIndex);

        assertEquals("CREATE INDEX \"sch\"\"ema\".\"idx\"\"name\" ON \"sch\"\"ema\".\"ta\"\"ble\" (\"co\"\"l\")", sql);
        assertFalse(sql.contains("ta\"ble\""));
    }

    @Test
    void buildCreateColumnSqlEscapesColumnName() {
        TableColumn column = new TableColumn();
        column.setName("co\"l");
        column.setColumnType("BOOLEAN");

        String sql = SUNDBColumnTypeEnum.BOOLEAN.buildCreateColumnSql(column);

        assertTrue(sql.startsWith("\"co\"\"l\" "));
    }

    @Test
    void buildCreateColumnSqlAcceptsValidUnitAndRejectsInjectedUnit() {
        TableColumn valid = new TableColumn();
        valid.setName("c1");
        valid.setColumnType("VARCHAR");
        valid.setColumnSize(10);
        valid.setUnit("byte");
        assertTrue(SUNDBColumnTypeEnum.VARCHAR.buildCreateColumnSql(valid).contains("VARCHAR(10 byte)"));

        TableColumn malicious = new TableColumn();
        malicious.setName("c1");
        malicious.setColumnType("VARCHAR");
        malicious.setColumnSize(10);
        malicious.setUnit("CHAR); DROP TABLE x--");
        assertThrows(IllegalArgumentException.class,
                () -> SUNDBColumnTypeEnum.VARCHAR.buildCreateColumnSql(malicious));
    }

    @Test
    void buildIndexScriptCanonicalizesAscOrDescAndRejectsInjection() {
        TableIndex tableIndex = new TableIndex();
        tableIndex.setSchemaName("s");
        tableIndex.setTableName("t");
        tableIndex.setName("i");
        TableIndexColumn column = new TableIndexColumn();
        column.setColumnName("c");
        column.setAscOrDesc("desc");
        tableIndex.setColumnList(List.of(column));
        assertTrue(SUNDBIndexTypeEnum.NORMAL.buildIndexScript(tableIndex).contains("(\"c\" DESC)"));

        TableIndexColumn malicious = new TableIndexColumn();
        malicious.setColumnName("c");
        malicious.setAscOrDesc("DESC); DROP TABLE \"U\"; --");
        tableIndex.setColumnList(List.of(malicious));
        assertThrows(IllegalArgumentException.class,
                () -> SUNDBIndexTypeEnum.NORMAL.buildIndexScript(tableIndex));
    }

    @Test
    void buildCreateColumnSqlAcceptsValidDefaultAndRejectsInjectedDefault() {
        TableColumn valid = new TableColumn();
        valid.setName("c1");
        valid.setColumnType("BOOLEAN");
        valid.setDefaultValue("CURRENT_TIMESTAMP");
        assertTrue(SUNDBColumnTypeEnum.BOOLEAN.buildCreateColumnSql(valid).contains("DEFAULT CURRENT_TIMESTAMP"));

        TableColumn validNegative = new TableColumn();
        validNegative.setName("c1");
        validNegative.setColumnType("INT");
        validNegative.setDefaultValue("-1");
        assertTrue(SUNDBColumnTypeEnum.INT.buildCreateColumnSql(validNegative).contains("DEFAULT -1"));

        TableColumn validSequence = new TableColumn();
        validSequence.setName("c1");
        validSequence.setColumnType("INT");
        validSequence.setDefaultValue("SEQ.NEXTVAL");
        assertTrue(SUNDBColumnTypeEnum.INT.buildCreateColumnSql(validSequence).contains("DEFAULT SEQ.NEXTVAL"));

        TableColumn malicious = new TableColumn();
        malicious.setName("c1");
        malicious.setColumnType("BOOLEAN");
        malicious.setDefaultValue("1; DROP TABLE x--");
        assertThrows(IllegalArgumentException.class,
                () -> SUNDBColumnTypeEnum.BOOLEAN.buildCreateColumnSql(malicious));
    }

    @Test
    void buildCreateColumnSqlAcceptsStringLiteralDefaults() {
        String[] valid = {"'Y'", "'0'", "'O''Brien'", "'1970-01-01'", "''",
                "DATE '2024-01-01'", "TIMESTAMP '2024-01-01 00:00:00'"};
        for (String literal : valid) {
            TableColumn column = new TableColumn();
            column.setName("c1");
            column.setColumnType("VARCHAR");
            column.setDefaultValue(literal);
            assertTrue(SUNDBColumnTypeEnum.VARCHAR.buildCreateColumnSql(column).contains("DEFAULT " + literal),
                    "literal should be accepted: " + literal);
        }
    }

    @Test
    void buildCreateColumnSqlRejectsDefaultValuesThatReshapeDdl() {
        String[] payloads = {
                "0) --",          // closes the column definition, comments out the line remainder
                "0 --",           // comment sequence in a bare token
                "1, x INT",       // injects an extra column definition into CREATE TABLE
                "now()",          // parentheses could close the column def; functions work without them
                "0); DROP TABLE x--",
                "'abc",           // unbalanced quote
                "'a' 'b'",        // literal concatenation smuggling
                "'a'||'b'",       // concatenation operator
                "'a'--",          // comment after literal
                "'a'; DROP TABLE x--",
                "TO_DATE('2024-01-01','YYYY-MM-DD')", // parens/comma could reshape DDL
                "SYS_GUID()",
                "DATE '2024-01-01'--" // comment after typed literal
        };
        for (String payload : payloads) {
            TableColumn column = new TableColumn();
            column.setName("c1");
            column.setColumnType("INT");
            column.setDefaultValue(payload);
            assertThrows(IllegalArgumentException.class,
                    () -> SUNDBColumnTypeEnum.INT.buildCreateColumnSql(column),
                    "payload should be rejected: " + payload);
        }
    }

    @Test
    void buildAlterTableRenameEscapesQuotedNames() {
        ai.chat2db.community.domain.api.model.metadata.Table oldTable =
                new ai.chat2db.community.domain.api.model.metadata.Table();
        oldTable.setSchemaName("sch\"ema");
        oldTable.setName("ta\"ble");
        ai.chat2db.community.domain.api.model.metadata.Table newTable =
                new ai.chat2db.community.domain.api.model.metadata.Table();
        newTable.setSchemaName("sch\"ema");
        newTable.setName("ne\"w");
        newTable.setColumnList(List.of());
        newTable.setIndexList(List.of());

        String sql = new SUNDBSqlBuilder().buildAlterTable(oldTable, newTable);

        assertTrue(sql.contains("ALTER TABLE \"sch\"\"ema\".\"ta\"\"ble\" RENAME TO \"ne\"\"w\""), sql);
        assertFalse(sql.contains("\"ta\"ble\""), sql);
    }

    @Test
    void copyTableQuotesIdentifiersInExecutedSql() throws Exception {
        String[] capturedSql = new String[1];
        java.sql.Connection connection = stubConnectionCapturingSql(capturedSql);

        new SUNDBDBManager().copyTable(connection, "db", "sch", "ta\"ble", "ne\"w", true);

        assertEquals("CREATE TABLE \"ne\"\"w\" AS SELECT * FROM \"ta\"\"ble\"", capturedSql[0]);
        assertFalse(capturedSql[0].contains("ta\"ble\" AS"));
    }

    @Test
    void exportTableColumnCommentEscapesLiteralsAndIdentifiers() throws Exception {
        String[] capturedSql = new String[1];
        java.util.Map<String, String> row = java.util.Map.of(
                "COLNAME", "co\"l",
                "COMMENT$", "O'Brien");
        java.sql.Connection connection = stubConnectionWithOneRow(capturedSql, row);
        StringBuilder sqlBuilder = new StringBuilder();

        java.lang.reflect.Method method = SUNDBDBManager.class.getDeclaredMethod(
                "exportTableColumnComment", java.sql.Connection.class, String.class, String.class, StringBuilder.class);
        method.setAccessible(true);
        method.invoke(new SUNDBDBManager(), connection, "sch'ema", "ta'ble", sqlBuilder);

        assertTrue(capturedSql[0].contains("SCHNAME = 'sch''ema'"), capturedSql[0]);
        assertTrue(capturedSql[0].contains("TVNAME = 'ta''ble'"), capturedSql[0]);
        assertFalse(capturedSql[0].contains("SCHNAME = 'sch'ema'"), capturedSql[0]);
        assertEquals("COMMENT ON COLUMN \"sch'ema\".\"ta'ble\".\"co\"\"l\" IS 'O''Brien';\n", sqlBuilder.toString());
    }

    @Test
    void getIndexNameAlwaysQuotesSchemaAndName() throws Exception {
        java.lang.reflect.Method method = SUNDBMetaData.class.getDeclaredMethod(
                "getIndexName", String.class, String.class);
        method.setAccessible(true);
        SUNDBMetaData metaData = new SUNDBMetaData();

        assertEquals("\"sch\"\"ema\".\"idx\"\"name\"", method.invoke(metaData, "sch\"ema", "idx\"name"));
        // system-style primary key index names get the same quoting as normal names
        assertEquals("\"sch\".\"T_PRIMARY_KEY_INDEX\"", method.invoke(metaData, "sch", "T_PRIMARY_KEY_INDEX"));
    }

    private static java.sql.Connection stubConnectionCapturingSql(String[] capturedSql) {
        return stubConnection(capturedSql, null);
    }

    private static java.sql.Connection stubConnectionWithOneRow(String[] capturedSql, java.util.Map<String, String> row) {
        return stubConnection(capturedSql, row);
    }

    private static java.sql.Connection stubConnection(String[] capturedSql, java.util.Map<String, String> row) {
        return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                SUNDBIdentifierProcessorTest.class.getClassLoader(),
                new Class<?>[]{java.sql.Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        capturedSql[0] = (String) args[0];
                        return stubPreparedStatement(row);
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static java.sql.PreparedStatement stubPreparedStatement(java.util.Map<String, String> row) {
        return (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                SUNDBIdentifierProcessorTest.class.getClassLoader(),
                new Class<?>[]{java.sql.PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "execute" -> {
                        // no result set
                        yield false;
                    }
                    case "executeQuery" -> {
                        yield stubResultSet(row);
                    }
                    case "close" -> {
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static java.sql.ResultSet stubResultSet(java.util.Map<String, String> row) {
        boolean[] consumed = new boolean[1];
        return (java.sql.ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                SUNDBIdentifierProcessorTest.class.getClassLoader(),
                new Class<?>[]{java.sql.ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> {
                        if (row != null && !consumed[0]) {
                            consumed[0] = true;
                            yield true;
                        }
                        yield false;
                    }
                    case "getString" -> {
                        yield row.get((String) args[0]);
                    }
                    case "close" -> {
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
