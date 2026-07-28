package ai.chat2db.plugin.presto.parser;

import ai.chat2db.community.domain.api.model.parser.statement.Statement;
import ai.chat2db.community.domain.api.service.db.ISqlBatchHandler;
import ai.chat2db.community.domain.api.service.task.ITaskProgressListener;
import ai.chat2db.plugin.mysql.parser.MysqlSqlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for plugin:presto-2: {@code PrestoSqlParser} was an empty subclass of
 * {@code MysqlSqlParser}, so Presto scripts were handled with MySQL-specific behaviour
 * (e.g. the MySQL-only {@code DELIMITER} client statement). The parser now uses its own
 * {@link PrestoDialect}.
 */
class PrestoSqlParserTest {

    private final PrestoSqlParser parser = new PrestoSqlParser();

    @TempDir
    Path tempDir;

    @Test
    void noLongerExtendsMysqlSqlParser() {
        assertFalse(MysqlSqlParser.class.isAssignableFrom(PrestoSqlParser.class),
                "PrestoSqlParser must not be a MysqlSqlParser subclass (MySQL dialect mismatch)");
    }

    @Test
    void prestoDialectHasNoMysqlDelimiterStatement() {
        PrestoDialect dialect = new PrestoDialect();
        assertFalse(dialect.isSetDelimiter("DELIMITER"), "Presto has no DELIMITER client statement");
        assertTrue(dialect.isStatementDelimiter(";"));
    }

    @Test
    void splitsStatementsOnSemicolons() {
        List<Statement> statements = parser.parserSqlScript("SELECT 1;\nSELECT 2;\n");
        assertEquals(2, statements.size());
    }

    @Test
    void keepsDoubleQuotedIdentifiersAndEmbeddedSemicolonsIntact() {
        // Presto quotes identifiers with double quotes; strings use single quotes.
        List<Statement> statements = parser.parserSqlScript(
                "SELECT \"order\", 'a;b' AS s FROM \"my-table\";\nSELECT * FROM t2;\n");
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("\"my-table\""));
        assertTrue(statements.get(0).getSql().contains("'a;b'"));
    }

    @Test
    void splitsPrestoSpecificSyntax() {
        List<Statement> statements = parser.parserSqlScript(
                "WITH t AS (SELECT * FROM x) SELECT * FROM t QUALIFY row_number() OVER () = 1;\n"
                        + "SELECT * FROM numbers CROSS JOIN UNNEST(ARRAY[1, 2]) AS u(n);\n");
        assertEquals(2, statements.size());
    }

    @Test
    void skipsCommentsWhenSplitting() {
        List<Statement> statements = parser.parserSqlScript(
                "-- leading comment; with semicolon\nSELECT 1;\n/* block ; comment */\nSELECT 2;\n");
        assertEquals(2, statements.size());
    }

    @Test
    void streamsScriptFileWithoutMysqlDelimiterHandling() throws Exception {
        File script = tempDir.resolve("script.sql").toFile();
        Files.writeString(script.toPath(), "SELECT 1;\nSELECT \"a\" FROM t2;\n", StandardCharsets.UTF_8);

        List<String> sqlTexts = new ArrayList<>();
        ISqlBatchHandler handler = new ISqlBatchHandler() {
            @Override
            public void handle(Statement statement) {
                sqlTexts.add(statement.getSql());
            }

            @Override
            public void flush() {
            }
        };
        ITaskProgressListener progressListener = (bytesRead, statementsParsed) -> {
        };

        int count = parser.parserSqlScript(script, progressListener, handler);

        assertEquals(2, count);
        assertEquals(2, sqlTexts.size());
        assertTrue(sqlTexts.get(1).contains("\"a\""));
    }
}
