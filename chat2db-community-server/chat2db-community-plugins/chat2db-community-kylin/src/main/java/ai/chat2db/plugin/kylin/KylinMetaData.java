package ai.chat2db.plugin.kylin;

import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.spi.DefaultMetaService;
import ai.chat2db.spi.DefaultSQLExecutor;
import ai.chat2db.spi.IDbMetaData;
import ai.chat2db.spi.ISQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class KylinMetaData extends DefaultMetaService implements IDbMetaData {

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        List<TableColumn> columns = DefaultSQLExecutor.getInstance()
                .columns(connection, databaseName, schemaName, tableName, null);
        List<TableIndex> indexes = DefaultSQLExecutor.getInstance()
                .indexes(connection, databaseName, schemaName, tableName);

        StringBuilder ddl = new StringBuilder(buildCreateTable(tableName, columns));
        for (TableIndex index : indexes) {
            String createIndex = buildCreateIndex(tableName, index);
            if (StringUtils.isNotBlank(createIndex)) {
                ddl.append('\n').append(createIndex);
            }
        }
        return ddl.toString();
    }

    private String buildCreateTable(String tableName, List<TableColumn> columns) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ")
                .append(quoteIdentifier(tableName))
                .append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(",\n");
            }
            sql.append('\t').append(buildColumnDefinition(columns.get(i)));
        }
        return sql.append("\n);").toString();
    }

    private String buildColumnDefinition(TableColumn column) {
        String dataType = StringUtils.defaultIfBlank(column.getColumnType(), "VARCHAR");
        StringBuilder definition = new StringBuilder(quoteIdentifier(column.getName()))
                .append(' ')
                .append(dataType);

        Integer columnSize = column.getColumnSize();
        if (StringUtils.equalsAnyIgnoreCase(dataType, "VARCHAR", "CHAR") && columnSize != null) {
            definition.append('(').append(columnSize).append(')');
        } else if (StringUtils.equalsAnyIgnoreCase(dataType, "DECIMAL", "NUMERIC") && columnSize != null) {
            definition.append('(').append(columnSize);
            if (column.getDecimalDigits() != null) {
                definition.append(',').append(column.getDecimalDigits());
            }
            definition.append(')');
        }

        if (Objects.equals(column.getNullable(), 0)) {
            definition.append(" NOT NULL");
        }
        if (StringUtils.isNotEmpty(column.getComment())) {
            definition.append(" COMMENT '")
                    .append(getSQLIdentifierProcessor().escapeString(column.getComment()))
                    .append('\'');
        }
        return definition.toString();
    }

    private String buildCreateIndex(String tableName, TableIndex index) {
        if (StringUtils.isBlank(index.getName()) || index.getColumnList() == null || index.getColumnList().isEmpty()) {
            return "";
        }

        StringJoiner columns = new StringJoiner(", ");
        for (TableIndexColumn column : index.getColumnList()) {
            columns.add(quoteIdentifier(column.getColumnName()));
        }
        return "CREATE " + (Boolean.TRUE.equals(index.getUnique()) ? "UNIQUE " : "")
                + "INDEX " + quoteIdentifier(index.getName())
                + " ON " + quoteIdentifier(tableName)
                + " (" + columns + ");";
    }

    private String quoteIdentifier(String identifier) {
        ISQLIdentifierProcessor identifierProcessor = getSQLIdentifierProcessor();
        return identifierProcessor.quoteIdentifierAlways(identifier);
    }
}
