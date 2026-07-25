package ai.chat2db.plugin.sqlserver;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerDBManagerTest {

    @Test
    void shouldRewriteQualifiedTableDdlAndPreserveCopyableStructure() {
        String ddl = """
                CREATE TABLE [sales].[orders]
                (
                    [id] BIGINT identity,
                    [computed_total] AS ([id] + 1),
                    [version] timestamp,
                    [status] int default ((1)),
                    constraint PK_orders
                    primary key ([id]),
                    constraint FK_orders_parent
                    foreign key ([parent_id])
                    references [sales].[orders] ([id]),
                    constraint FK_orders_customer
                    foreign key ([customer_id])
                    references customers ([id])
                )
                GO
                CREATE NONCLUSTERED INDEX [IX_orders_status]
                 ON [sales].[orders] ([status])
                go
                exec sp_addextendedproperty 'MS_Description',N'index comment','SCHEMA',N'sales','TABLE',N'orders','INDEX',N'IX_orders_status'
                GO
                exec sp_addextendedproperty 'MS_Description',N'constraint comment','SCHEMA',N'sales','TABLE',N'orders','CONSTRAINT',N'PK_orders'
                GO
                """;

        List<String> batches = SqlServerDBManager.prepareCopyDdlBatches(
                ddl, "catalog", "sales", "orders", "orders_copy");

        assertEquals(3, batches.size());
        assertTrue(batches.get(0).startsWith("CREATE TABLE [sales].[orders_copy]"));
        assertTrue(batches.get(0).contains("[id] BIGINT identity"));
        assertTrue(batches.get(0).contains("[computed_total] AS ([id] + 1)"));
        assertTrue(batches.get(0).contains("[version] timestamp"));
        assertTrue(batches.get(0).contains("[status] int default ((1))"));
        assertTrue(batches.get(0).contains("primary key ([id])"));
        assertTrue(batches.get(0).contains("references [sales].[orders_copy] ([id])"));
        assertTrue(batches.get(0).contains("references customers ([id])"));
        assertFalse(batches.get(0).contains("constraint PK_orders"));
        assertFalse(batches.get(0).contains("constraint FK_orders_parent"));
        assertTrue(batches.get(1).contains("INDEX [IX_orders_status]"));
        assertTrue(batches.get(1).contains("ON [sales].[orders_copy] ([status])"));
        assertTrue(batches.get(2).contains("'TABLE',N'orders_copy','INDEX',N'IX_orders_status'"));
        assertFalse(batches.stream().anyMatch(batch -> batch.contains("constraint comment")));
    }

    @Test
    void shouldRewriteCurrentUnqualifiedCreateTableFormat() {
        List<String> batches = SqlServerDBManager.prepareCopyDdlBatches(
                "CREATE TABLE [orders]\n([id] int)\ngo\n",
                "catalog", "sales", "orders", "orders_copy");

        assertEquals(List.of("CREATE TABLE [sales].[orders_copy]\n([id] int)"), batches);
    }

    @Test
    void shouldNotSplitGoInsideStringLiteral() {
        List<String> batches = SqlServerDBManager.splitDdlBatches(
                "exec log_comment N'line one\ngo\nline three'\nGO ; -- batch\nSELECT 1;");

        assertEquals(List.of(
                "exec log_comment N'line one\ngo\nline three'",
                "SELECT 1;"), batches);
    }

    @Test
    void shouldUseTheSameExplicitColumnsForInsertAndSelect() {
        assertEquals(
                "INSERT INTO [catalog].[sales].[orders_copy] ([id], [name]) "
                        + "SELECT [id], [name] FROM [catalog].[sales].[orders]",
                SqlServerDBManager.buildCopyDataSql(
                        "[catalog].[sales].[orders_copy]", "[id], [name]", "[catalog].[sales].[orders]"));
    }

    @Test
    void shouldExcludeGeneratedColumnsButKeepIdentityAndDefaultBackedColumns() {
        assertFalse(SqlServerDBManager.isCopyableColumn("([quantity] * [price])", "decimal"));
        assertFalse(SqlServerDBManager.isCopyableColumn(null, "timestamp"));
        assertFalse(SqlServerDBManager.isCopyableColumn(null, "rowversion"));
        assertTrue(SqlServerDBManager.isCopyableColumn(null, "bigint"));
        assertTrue(SqlServerDBManager.isCopyableColumn(null, "int"));
    }

    @Test
    void shouldEscapeEveryQualifiedIdentifierPart() {
        assertEquals("[catalog]]archive].[sales].[orders]]2026]",
                SqlServerDBManager.buildFullTableName("catalog]archive", "sales", "orders]2026"));
        assertEquals("[catalog].[sales].[orders]",
                SqlServerDBManager.buildFullTableName("[catalog]", "[sales]", "[orders]"));
    }
}
