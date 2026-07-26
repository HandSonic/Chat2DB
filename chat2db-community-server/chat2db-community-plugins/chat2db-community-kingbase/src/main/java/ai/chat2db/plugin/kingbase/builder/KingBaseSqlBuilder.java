package ai.chat2db.plugin.kingbase.builder;

import ai.chat2db.spi.constant.SQLConstants;

import ai.chat2db.plugin.kingbase.enums.type.KingBaseColumnTypeEnum;
import ai.chat2db.plugin.kingbase.enums.type.KingBaseIndexTypeEnum;
import ai.chat2db.spi.DefaultSqlBuilder;
import ai.chat2db.spi.model.request.PageLimitRequest;
import ai.chat2db.community.domain.api.model.account.*;
import ai.chat2db.community.domain.api.model.async.*;
import ai.chat2db.community.domain.api.config.*;
import ai.chat2db.spi.model.datasource.*;
import ai.chat2db.community.domain.api.model.form.*;
import ai.chat2db.community.domain.api.model.metadata.*;
import ai.chat2db.community.domain.api.model.result.*;
import ai.chat2db.community.domain.api.model.sql.*;
import ai.chat2db.spi.model.value.*;
import ai.chat2db.community.domain.api.model.view.*;
import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import static ai.chat2db.plugin.kingbase.constant.KingBaseSqlBuilderConstants.*;
import ai.chat2db.plugin.kingbase.KingBaseSqlGuards;
import ai.chat2db.plugin.kingbase.identifier.KingBaseSQLIdentifierProcessor;
public class KingBaseSqlBuilder extends DefaultSqlBuilder {




















    @Override
    public String buildCreateTable(Table table, TableBuilderConfig tableBuilderConfig) {
        StringBuilder script = new StringBuilder();
        script.append(SQL_CREATE_TABLE);
        script.append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(table.getName())).append(SQLConstants.SPACE_OPEN_PARENTHESIS).append(SQLConstants.SPACE).append(SQLConstants.LINE_SEPARATOR);
        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            KingBaseColumnTypeEnum typeEnum = KingBaseColumnTypeEnum.getByType(column.getColumnType());
            if(typeEnum ==null){
                continue;
            }
            script.append(SQLConstants.TAB).append(typeEnum.buildCreateColumnSql(column)).append(SQLConstants.COMMA_LINE_SEPARATOR);
        }
        Map<Boolean, List<TableIndex>> tableIndexMap = table.getIndexList().stream()
                .collect(Collectors.partitioningBy(v -> KingBaseIndexTypeEnum.NORMAL.getName().equals(v.getType())));
        List<TableIndex> constraintList = tableIndexMap.get(Boolean.FALSE);
        if (CollectionUtils.isNotEmpty(constraintList)) {
            for (TableIndex index : constraintList) {
                if (StringUtils.isBlank(index.getName()) || StringUtils.isBlank(index.getType())) {
                    continue;
                }
                KingBaseIndexTypeEnum indexTypeEnum = KingBaseIndexTypeEnum.getByType(index.getType());
                if(indexTypeEnum == null){
                    continue;
                }
                script.append(SQLConstants.TAB).append(SQLConstants.EMPTY).append(indexTypeEnum.buildIndexScript(index));
                script.append(SQLConstants.COMMA_LINE_SEPARATOR);
            }

        }
        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append(SQLConstants.LINE_SEPARATOR_CLOSE_PARENTHESIS);
        if(StringUtils.isNotBlank(table.getTablespace())){
            script.append(" TABLESPACE ").append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(table.getTablespace())).append(SQLConstants.SEMICOLON);
        }else {
            script.append(SQL_TABLESPACE_DOUBLE_QUOTE_SYS_DEFAULT_DOUBLE_QUOTE_SEMICOLON);
        }
        List<TableIndex> tableIndexList = tableIndexMap.get(Boolean.TRUE);
        for (TableIndex tableIndex : tableIndexList) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            script.append(SQLConstants.LINE_SEPARATOR);
            KingBaseIndexTypeEnum indexTypeEnum = KingBaseIndexTypeEnum.getByType(tableIndex.getType());
            if(indexTypeEnum == null){
                continue;
            }
            script.append(SQLConstants.EMPTY).append(indexTypeEnum.buildIndexScript(tableIndex)).append(SQLConstants.SEMICOLON);
        }
        if (StringUtils.isNotBlank(table.getComment())) {
            script.append(SQLConstants.LINE_SEPARATOR);
            script.append(SQL_COMMENT_TABLE).append(SQLConstants.SPACE).append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(table.getName())).append(SQLConstants.SQL_IS_SINGLE_QUOTE)
                    .append(KingBaseSQLIdentifierProcessor.INSTANCE.escapeString(table.getComment())).append(SQLConstants.SINGLE_QUOTE_SEMICOLON_LINE_SEPARATOR);
        }
        List<TableColumn> tableColumnList = table.getColumnList().stream().filter(v -> StringUtils.isNotBlank(v.getComment())).toList();
        for (TableColumn tableColumn : tableColumnList) {
            KingBaseColumnTypeEnum typeEnum = KingBaseColumnTypeEnum.getByType(tableColumn.getColumnType());
            if(typeEnum == null){
                continue;
            }
            script.append(typeEnum.buildComment(tableColumn, typeEnum)).append(SQLConstants.LINE_SEPARATOR);
            ;
        }
        List<TableIndex> indexList = table.getIndexList().stream().filter(v -> StringUtils.isNotBlank(v.getComment())).toList();
        for (TableIndex index : indexList) {
            KingBaseIndexTypeEnum indexEnum = KingBaseIndexTypeEnum.getByType(index.getType());
            if(indexEnum == null){
                continue;
            }
            script.append(indexEnum.buildIndexComment(index)).append(SQLConstants.LINE_SEPARATOR);
        }

        return script.toString();
    }

    @Override
    public String buildAlterTable(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();
        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            script.append(SQL_ALTER_TABLE).append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(oldTable.getName()));
            script.append(SQLConstants.TAB).append(SQL_RENAME).append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(newTable.getName())).append(SQLConstants.SEMICOLON_LINE_SEPARATOR);

        }
        newTable.setColumnList(newTable.getColumnList().stream().filter(v -> StringUtils.isNotBlank(v.getEditStatus())).toList());
        newTable.setIndexList(newTable.getIndexList().stream().filter(v -> StringUtils.isNotBlank(v.getEditStatus())).toList());
        List<TableColumn> columnNameList = newTable.getColumnList().stream().filter(v ->
                v.getOldName() != null && !StringUtils.equals(v.getOldName(), v.getName())).toList();
        for (TableColumn tableColumn : columnNameList) {
            script.append(SQL_ALTER_TABLE).append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(newTable.getName())).append(SQLConstants.SPACE).append("RENAME COLUMN ")
                    .append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(tableColumn.getOldName())).append(" TO ").append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(tableColumn.getName())).append(SQLConstants.SEMICOLON_LINE_SEPARATOR);
        }

        Map<Boolean, List<TableIndex>> tableIndexMap = newTable.getIndexList().stream()
                .collect(Collectors.partitioningBy(v -> KingBaseIndexTypeEnum.NORMAL.getName().equals(v.getType())));
        StringBuilder scriptModify = new StringBuilder();
        Boolean modify = false;
        scriptModify.append(SQL_ALTER_TABLE).append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(newTable.getName())).append(" \n");
        for (TableColumn tableColumn : newTable.getColumnList()) {
            KingBaseColumnTypeEnum typeEnum = KingBaseColumnTypeEnum.getByType(tableColumn.getColumnType());
            if(typeEnum == null){
                continue;
            }
            scriptModify.append(SQLConstants.TAB).append(typeEnum.buildModifyColumn(tableColumn)).append(SQLConstants.COMMA_LINE_SEPARATOR);
            modify = true;

        }
        for (TableIndex tableIndex : tableIndexMap.get(Boolean.FALSE)) {
            if (StringUtils.isNotBlank(tableIndex.getType())) {
                KingBaseIndexTypeEnum indexTypeEnum = KingBaseIndexTypeEnum.getByType(tableIndex.getType());
                if(indexTypeEnum == null){
                    continue;
                }
                scriptModify.append(SQLConstants.TAB).append(indexTypeEnum.buildModifyIndex(tableIndex)).append(SQLConstants.COMMA_LINE_SEPARATOR);
                modify = true;
            }
        }

        if (BooleanUtils.isTrue(modify)) {
            script.append(scriptModify);
            script = new StringBuilder(script.substring(0, script.length() - 2));
            script.append(SQLConstants.SEMICOLON_LINE_SEPARATOR);
        }
        for (TableIndex tableIndex : tableIndexMap.get(Boolean.TRUE)) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                KingBaseIndexTypeEnum indexTypeEnum = KingBaseIndexTypeEnum.getByType(tableIndex.getType());
                if(indexTypeEnum == null){
                    continue;
                }
                script.append(indexTypeEnum.buildModifyIndex(tableIndex)).append(SQLConstants.SEMICOLON_LINE_SEPARATOR);
            }
        }
        if (!StringUtils.equals(oldTable.getComment(), newTable.getComment())) {
            script.append(SQLConstants.LINE_SEPARATOR);
            script.append(SQL_COMMENT_TABLE).append(SQLConstants.SPACE).append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(newTable.getName())).append(SQLConstants.SQL_IS_SINGLE_QUOTE)
                    .append(KingBaseSQLIdentifierProcessor.INSTANCE.escapeString(newTable.getComment())).append(SQLConstants.SINGLE_QUOTE_SEMICOLON_LINE_SEPARATOR);
        }
        for (TableColumn tableColumn : newTable.getColumnList()) {
            KingBaseColumnTypeEnum typeEnum = KingBaseColumnTypeEnum.getByType(tableColumn.getColumnType());
            if(typeEnum == null){
                continue;
            }
            script.append(typeEnum.buildComment(tableColumn, typeEnum)).append(SQLConstants.LINE_SEPARATOR);
            ;
        }
        List<TableIndex> indexList = newTable.getIndexList().stream().filter(v -> StringUtils.isNotBlank(v.getComment())).toList();
        for (TableIndex index : indexList) {
            KingBaseIndexTypeEnum indexEnum = KingBaseIndexTypeEnum.getByType(index.getType());
            if(indexEnum == null){
                continue;
            }
            script.append(indexEnum.buildIndexComment(index)).append(SQLConstants.LINE_SEPARATOR);
        }

        return script.toString();
    }

    @Override
    public String buildPageLimit(PageLimitRequest request) {
        String sql = request.getSql();
        int offset = request.getOffset();
        int pageNo = request.getPageNo();
        int pageSize = request.getPageSize();
        StringBuilder sqlStr = new StringBuilder(sql.length() + 17);
        sqlStr.append(sql);
        if (offset == 0) {
            sqlStr.append(SQL_LIMIT);
            sqlStr.append(pageSize);
        } else {
            sqlStr.append(SQL_LIMIT);
            sqlStr.append(pageSize);
            sqlStr.append(SQL_OFFSET);
            sqlStr.append(offset);
        }
        return sqlStr.toString();
    }

    @Override
    public String buildCreateDatabase(Database database) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append(SQL_CREATE_DATABASE+KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(database.getName()));
        String owner = database.getOwner();
        if (StringUtils.isBlank(owner)) {
            owner = SYSTEM_KEYWORD;
        }
        sqlBuilder.append(" WITH  OWNER = ").append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(owner));
        if (StringUtils.isNotBlank(database.getCharset())) {
            sqlBuilder.append(SQL_ENCODING).append(KingBaseSqlGuards.requireSafeExpression(database.getCharset(), "database charset")).append(SQLConstants.EMPTY);
        }
        sqlBuilder.append(SQLConstants.SEMICOLON_LINE_SEPARATOR);

        if (StringUtils.isNotBlank(database.getComment())) {
            sqlBuilder.append(SQL_COMMENT_DATABASE).append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(database.getName())).append(SQLConstants.SQL_IS_SINGLE_QUOTE).append(KingBaseSQLIdentifierProcessor.INSTANCE.escapeString(database.getComment())).append(SQLConstants.SINGLE_QUOTE_SEMICOLON);
        }
        return sqlBuilder.toString();
    }


    @Override
    public String buildCreateSchema(Schema schema){
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append(SQL_CREATE_SCHEMA+KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(schema.getName())+SQLConstants.EMPTY);
        String owner = schema.getOwner();
        if(StringUtils.isBlank(schema.getOwner())){
            owner = SYSTEM_KEYWORD;
        }
        sqlBuilder.append(" AUTHORIZATION ").append(KingBaseSQLIdentifierProcessor.INSTANCE.quoteIdentifierAlways(owner));
        return sqlBuilder.toString();
    }

}
