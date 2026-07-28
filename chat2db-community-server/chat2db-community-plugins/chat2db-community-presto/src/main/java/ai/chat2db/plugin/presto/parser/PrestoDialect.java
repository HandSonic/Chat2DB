package ai.chat2db.plugin.presto.parser;

import ai.chat2db.mysql.parser.base.MySqlLexer;
import ai.chat2db.spi.parser.dialect.AbstractSQLDialect;

import java.util.Set;

/**
 * Presto SQL dialect.
 *
 * <p>No Presto ANTLR grammar is bundled in this repository, so the MySQL lexer is
 * reused for tokenization, but dialect decisions are Presto-specific:
 * <ul>
 *     <li>Presto has no {@code DELIMITER} client statement, so unlike
 *     {@code MysqlDialect} no DELIMITER set-delimiter is registered.</li>
 *     <li>MySQL-specific {@code /*! ... *!} executable comments do not exist in
 *     Presto; only line ({@code --}) and block ({@code /* ... * /}) comments are
 *     treated as comments.</li>
 * </ul>
 */
public class PrestoDialect extends AbstractSQLDialect {

    private static final Set<Integer> PRESTO_COMMENT_TOKENS =
            Set.of(MySqlLexer.COMMENT_INPUT, MySqlLexer.LINE_COMMENT);

    @Override
    public Set<Integer> getCommentTokens() {
        return PRESTO_COMMENT_TOKENS;
    }

    @Override
    public boolean isComment(int tokenType) {
        return getCommentTokens().contains(tokenType);
    }
}
