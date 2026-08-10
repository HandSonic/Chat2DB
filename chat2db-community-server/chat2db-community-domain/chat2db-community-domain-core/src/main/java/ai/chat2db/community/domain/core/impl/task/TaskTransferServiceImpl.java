package ai.chat2db.community.domain.core.impl.task;

import ai.chat2db.community.domain.api.enums.ExportScopeTypeEnum;
import ai.chat2db.community.domain.api.enums.TaskTypeEnum;
import ai.chat2db.community.domain.api.model.async.AsyncContext;
import ai.chat2db.community.domain.api.model.async.ExportFileTarget;
import ai.chat2db.community.domain.api.model.request.task.TaskFileImportRequest;
import ai.chat2db.community.domain.api.model.request.task.TaskOtherFileExportRequest;
import ai.chat2db.community.domain.api.model.request.task.TaskRecordCreateRequest;
import ai.chat2db.community.domain.api.model.request.task.TaskRecordUpdateRequest;
import ai.chat2db.community.domain.api.model.request.task.TaskSqlFileExportRequest;
import ai.chat2db.community.domain.api.model.task.ExportAsyncContext;
import ai.chat2db.community.domain.api.model.task.ImportAsyncContext;
import ai.chat2db.community.domain.api.service.task.ITaskDataExportService;
import ai.chat2db.community.domain.api.service.task.ITaskDataImportService;
import ai.chat2db.community.domain.api.service.task.ITaskExecutionService;
import ai.chat2db.community.domain.api.service.task.ITaskExportService;
import ai.chat2db.community.domain.api.service.task.ITaskFileService;
import ai.chat2db.community.domain.api.service.task.ITaskRecordService;
import ai.chat2db.community.domain.api.service.task.ITaskSchedulerService;
import ai.chat2db.community.domain.api.service.task.ITaskTransferService;
import ai.chat2db.community.tools.util.ContextUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskTransferServiceImpl implements ITaskTransferService {

    private final ITaskExecutionService taskExecutionService;
    private final ITaskExportService taskExportService;
    private final ITaskDataExportService taskDataExportService;
    private final ITaskDataImportService taskDataImportService;
    private final ITaskRecordService taskRecordService;
    private final ITaskFileService taskFileService;
    private final ITaskSchedulerService taskSchedulerService;

    public TaskTransferServiceImpl(ITaskExecutionService taskExecutionService,
            ITaskExportService taskExportService,
            ITaskDataExportService taskDataExportService,
            ITaskDataImportService taskDataImportService,
            ITaskRecordService taskRecordService,
            ITaskFileService taskFileService,
            ITaskSchedulerService taskSchedulerService) {
        this.taskExecutionService = taskExecutionService;
        this.taskExportService = taskExportService;
        this.taskDataExportService = taskDataExportService;
        this.taskDataImportService = taskDataImportService;
        this.taskRecordService = taskRecordService;
        this.taskFileService = taskFileService;
        this.taskSchedulerService = taskSchedulerService;
    }

    @Override
    public Long exportSqlFile(TaskSqlFileExportRequest request) {
        if (StringUtils.isBlank(request.getExportPath())) {
            request.setExportPath(taskFileService.defaultExportPath());
        }
        ExportScopeTypeEnum scope = ExportScopeTypeEnum.from(request.getScope());
        if (scope == null) {
            throw new IllegalArgumentException("Unsupported export scope: " + request.getScope());
        }
        switch (scope) {
            case ALL:
                request.setContainData(Boolean.TRUE);
                break;
            case SCHEMA:
            case TABLE:
                request.setContainData(Boolean.FALSE);
                break;
            default:
                throw new IllegalArgumentException("Unsupported export scope: " + request.getScope());
        }
        ExportFileTarget exportFileTarget = taskFileService.prepareSqlExportTarget(request.getExportPath(),
                request.getDatabaseName(), request.getSchemaName(), request.getTableNames(), request.getExportFileName(),
                request.getOverwriteExistingFile());
        return submitSqlExport(request, exportFileTarget);
    }

    @Override
    public Long exportOtherFile(TaskOtherFileExportRequest request) {
        if (StringUtils.isBlank(request.getExportPath())) {
            request.setExportPath(taskFileService.defaultExportPath());
        }
        if (CollectionUtils.isEmpty(request.getTableNames())) {
            throw new IllegalArgumentException("Export table names must not be empty");
        }
        ExportFileTarget exportFileTarget = taskFileService.prepareOtherExportTarget(request.getExportPath(),
                request.getDatabaseName(), request.getSchemaName(), request.getTableNames(), request.getExportType(),
                request.getExportFileName(), request.getOverwriteExistingFile());
        return submitOtherExport(request, exportFileTarget);
    }

    @Override
    public Long importSqlFile(TaskFileImportRequest request) {
        Long taskId = createImportTask(request.getDatabaseName(), request.getSchemaName(), request.getTableName());
        ImportAsyncContext asyncContext = createImportContext(taskId, "SQL", request);
        taskSchedulerService.submit(taskId, asyncContext,
                taskExecutionService.withCurrentConnectionContext(ContextUtils.queryContext(),
                        () -> taskDataImportService.importOtherFile(asyncContext)));
        return taskId;
    }

    @Override
    public Long importOtherFile(TaskFileImportRequest request) {
        Long taskId = createImportTask(request.getDatabaseName(), request.getSchemaName(), request.getTableName());
        ImportAsyncContext asyncContext = createImportContext(taskId, request.getImportType(), request);
        taskSchedulerService.submit(taskId, asyncContext,
                taskExecutionService.withCurrentConnectionContext(ContextUtils.queryContext(),
                        () -> taskDataImportService.importOtherFile(asyncContext)));
        return taskId;
    }

    private Long submitSqlExport(TaskSqlFileExportRequest request, ExportFileTarget exportFileTarget) {
        Long taskId = null;
        AsyncContext asyncContext = null;
        try {
            taskId = createExportTask(request.getDatabaseName(), request.getSchemaName(), request.getTableNames());
            AsyncContext sqlAsyncContext = taskFileService.createSqlExportContext(taskId, request.getExportPath(),
                    request.getDatabaseName(), request.getSchemaName(), request.getTableNames(), request.isContainData(),
                    taskSchedulerService.asyncCall(taskId), ContextUtils.queryContext(), exportFileTarget);
            asyncContext = sqlAsyncContext;
            taskSchedulerService.submit(taskId, sqlAsyncContext,
                    taskExecutionService.withCurrentConnectionContext(ContextUtils.queryContext(),
                            () -> taskExportService.exportSqlFile(request, sqlAsyncContext)));
            return taskId;
        } catch (RuntimeException e) {
            discardExportTarget(asyncContext, exportFileTarget);
            markTaskAsError(taskId, e);
            throw e;
        }
    }

    private Long submitOtherExport(TaskOtherFileExportRequest request, ExportFileTarget exportFileTarget) {
        Long taskId = null;
        AsyncContext asyncContext = null;
        try {
            taskId = createExportTask(request.getDatabaseName(), request.getSchemaName(), request.getTableNames());
            ExportAsyncContext exportAsyncContext = taskFileService.createOtherExportContext(taskId,
                    request.getExportPath(), request.getDatabaseName(), request.getSchemaName(), request.getTableNames(),
                    request.getExportType(), request.getContainsHeader(), taskSchedulerService.asyncCall(taskId),
                    ContextUtils.queryContext(), exportFileTarget);
            asyncContext = exportAsyncContext;
            taskSchedulerService.submit(taskId, exportAsyncContext,
                    taskExecutionService.withCurrentConnectionContext(ContextUtils.queryContext(),
                            () -> taskDataExportService.exportOtherFile(exportAsyncContext)));
            return taskId;
        } catch (RuntimeException e) {
            discardExportTarget(asyncContext, exportFileTarget);
            markTaskAsError(taskId, e);
            throw e;
        }
    }

    private void discardExportTarget(AsyncContext asyncContext, ExportFileTarget exportFileTarget) {
        if (asyncContext != null) {
            asyncContext.stop();
            asyncContext.finish();
        } else if (exportFileTarget != null) {
            exportFileTarget.discard();
        }
    }

    private void markTaskAsError(Long taskId, RuntimeException exception) {
        if (taskId == null) {
            return;
        }
        try {
            TaskRecordUpdateRequest request = new TaskRecordUpdateRequest();
            request.setId(taskId);
            request.setTaskStatus("ERROR");
            request.setDownloadUrl("");
            request.setErrorLog(exception.getMessage());
            taskRecordService.updateTask(request);
        } catch (RuntimeException ignored) {
            // Preserve the original scheduling/context failure.
        }
    }

    private ImportAsyncContext createImportContext(Long taskId, String importType, TaskFileImportRequest request) {
        return taskFileService.createImportContext(taskId, importType, request.getTableName(), request.getFileName(),
                taskSchedulerService.asyncCall(taskId), ContextUtils.queryContext());
    }

    private Long createExportTask(String databaseName, String schemaName, List<String> tableNames) {
        String taskName = taskFileService.resolveTaskName(databaseName, schemaName, tableNames);
        TaskRecordCreateRequest request = baseTaskCreateRequest("Export " + taskName,
                TaskTypeEnum.DOWNLOAD_TABLE_STRUCTURE.name(), databaseName, schemaName);
        if (CollectionUtils.isNotEmpty(tableNames)) {
            request.setTableName(String.join(",", tableNames));
        }
        return createTask(request);
    }

    private Long createImportTask(String databaseName, String schemaName, String tableName) {
        String taskName = StringUtils.defaultIfBlank(StringUtils.defaultIfBlank(schemaName, databaseName), tableName);
        TaskRecordCreateRequest request = baseTaskCreateRequest("Import " + taskName,
                TaskTypeEnum.UPLOAD_TABLE_DATA.name(), databaseName, schemaName);
        if (StringUtils.isNotEmpty(tableName)) {
            request.setTableName(tableName);
        }
        return createTask(request);
    }

    private TaskRecordCreateRequest baseTaskCreateRequest(String taskName, String taskType, String databaseName,
            String schemaName) {
        TaskRecordCreateRequest request = new TaskRecordCreateRequest();
        request.setTaskName(taskName);
        request.setTaskType(taskType);
        request.setDatabaseName(databaseName);
        request.setSchemaName(schemaName);
        return request;
    }

    private Long createTask(TaskRecordCreateRequest request) {
        Long taskId = taskRecordService.createTask(request);
        if (taskId == null) {
            throw new IllegalStateException("Task creation failed");
        }
        return taskId;
    }
}
