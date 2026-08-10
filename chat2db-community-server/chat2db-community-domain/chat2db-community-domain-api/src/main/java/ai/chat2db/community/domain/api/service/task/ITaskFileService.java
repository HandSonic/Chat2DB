package ai.chat2db.community.domain.api.service.task;

import ai.chat2db.community.domain.api.model.async.AsyncContext;
import ai.chat2db.community.domain.api.model.async.ExportFileTarget;
import ai.chat2db.community.domain.api.model.task.ExportAsyncContext;
import ai.chat2db.community.domain.api.model.task.ImportAsyncContext;
import ai.chat2db.community.tools.model.Context;

import java.util.List;

/**
 * Creates file-backed async task contexts.
 */
public interface ITaskFileService {

    String defaultExportPath();

    String resolveTaskName(String databaseName, String schemaName, List<String> tableNames);

    AsyncContext createSqlExportContext(Long taskId, String exportPath, String databaseName, String schemaName,
            List<String> tableNames, boolean containData, ITaskAsyncCall asyncCall, Context context);

    /**
     * Compatibility overload for callers that choose a SQL file name. New
     * parameters are appended so existing implementations and binary callers
     * continue to use the original contract above.
     */
    default AsyncContext createSqlExportContext(Long taskId, String exportPath, String databaseName,
            String schemaName, List<String> tableNames, boolean containData, ITaskAsyncCall asyncCall,
            Context context, String exportFileName, Boolean overwriteExistingFile) {
        return createSqlExportContext(taskId, exportPath, databaseName, schemaName, tableNames, containData,
                asyncCall, context);
    }

    /**
     * Prepares a staged export target before the task record is created.
     * Implementations without staged-output support may return {@code null}.
     */
    default ExportFileTarget prepareSqlExportTarget(String exportPath, String databaseName, String schemaName,
            List<String> tableNames, String exportFileName, Boolean overwriteExistingFile) {
        return null;
    }

    default AsyncContext createSqlExportContext(Long taskId, String exportPath, String databaseName,
            String schemaName, List<String> tableNames, boolean containData, ITaskAsyncCall asyncCall,
            Context context, ExportFileTarget exportFileTarget) {
        return createSqlExportContext(taskId, exportPath, databaseName, schemaName, tableNames, containData,
                asyncCall, context);
    }

    ExportAsyncContext createOtherExportContext(Long taskId, String exportPath, String databaseName, String schemaName,
            List<String> tableNames, String exportType, Boolean containsHeader, ITaskAsyncCall asyncCall,
            Context context);

    /**
     * Compatibility overload for callers that choose a non-SQL file name.
     * New parameters are appended after the original signature.
     */
    default ExportAsyncContext createOtherExportContext(Long taskId, String exportPath, String databaseName,
            String schemaName, List<String> tableNames, String exportType, Boolean containsHeader,
            ITaskAsyncCall asyncCall, Context context, String exportFileName, Boolean overwriteExistingFile) {
        return createOtherExportContext(taskId, exportPath, databaseName, schemaName, tableNames, exportType,
                containsHeader, asyncCall, context);
    }

    default ExportFileTarget prepareOtherExportTarget(String exportPath, String databaseName, String schemaName,
            List<String> tableNames, String exportType, String exportFileName, Boolean overwriteExistingFile) {
        return null;
    }

    default ExportAsyncContext createOtherExportContext(Long taskId, String exportPath, String databaseName,
            String schemaName, List<String> tableNames, String exportType, Boolean containsHeader,
            ITaskAsyncCall asyncCall, Context context, ExportFileTarget exportFileTarget) {
        return createOtherExportContext(taskId, exportPath, databaseName, schemaName, tableNames, exportType,
                containsHeader, asyncCall, context);
    }

    ImportAsyncContext createImportContext(Long taskId, String importType, String tableName, String fileName,
            ITaskAsyncCall asyncCall, Context context);
}
