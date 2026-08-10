package ai.chat2db.community.domain.core.impl.task;

import ai.chat2db.community.domain.api.model.async.AsyncContext;
import ai.chat2db.community.domain.api.model.async.ExportFileTarget;
import ai.chat2db.community.domain.api.model.task.ExportAsyncContext;
import ai.chat2db.community.domain.api.model.task.ImportAsyncContext;
import ai.chat2db.community.domain.api.service.task.ITaskAsyncCall;
import ai.chat2db.community.domain.api.service.task.ITaskFileService;
import ai.chat2db.community.tools.model.Context;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TaskFileServiceImpl implements ITaskFileService {

    private static final String DEFAULT_TASK_NAME = "chat2db_export";

    private static final Map<String, String> EXPORT_TYPE_SUFFIXES = Map.of(
            "CSV", "csv",
            "XLSX", "xlsx",
            "XLS", "xls",
            "JSON", "json",
            "SQL", "sql");

    @Override
    public String defaultExportPath() {
        return System.getProperty("user.dir");
    }

    @Override
    public String resolveTaskName(String databaseName, String schemaName, List<String> tableNames) {
        String taskName = "";
        if (CollectionUtils.isNotEmpty(tableNames)) {
            if (tableNames.size() > 2) {
                taskName = tableNames.get(0) + "_" + tableNames.get(1);
            } else {
                taskName = String.join("_", tableNames);
            }
        }
        if (StringUtils.isBlank(taskName) && StringUtils.isNotBlank(schemaName)) {
            taskName = schemaName;
        }
        if (StringUtils.isBlank(taskName) && StringUtils.isNotBlank(databaseName)) {
            taskName = databaseName;
        }
        return StringUtils.defaultIfBlank(taskName, DEFAULT_TASK_NAME);
    }

    @Override
    public AsyncContext createSqlExportContext(Long taskId, String exportPath, String databaseName, String schemaName,
            List<String> tableNames, boolean containData, ITaskAsyncCall asyncCall, Context context) {
        return createSqlExportContext(taskId, exportPath, databaseName, schemaName, tableNames, containData, asyncCall,
                context, null, Boolean.FALSE);
    }

    @Override
    public AsyncContext createSqlExportContext(Long taskId, String exportPath, String databaseName, String schemaName,
            List<String> tableNames, boolean containData, ITaskAsyncCall asyncCall, Context context,
            String exportFileName, Boolean overwriteExistingFile) {
        ExportFileTarget exportFileTarget = prepareSqlExportTarget(exportPath, databaseName, schemaName, tableNames,
                exportFileName, overwriteExistingFile);
        return createSqlExportContext(taskId, exportPath, databaseName, schemaName, tableNames, containData, asyncCall,
                context, exportFileTarget);
    }

    @Override
    public ExportFileTarget prepareSqlExportTarget(String exportPath, String databaseName, String schemaName,
            List<String> tableNames, String exportFileName, Boolean overwriteExistingFile) {
        return prepareExportTarget(exportPath, exportFileName, resolveTaskName(databaseName, schemaName, tableNames),
                "sql", overwriteExistingFile);
    }

    @Override
    public AsyncContext createSqlExportContext(Long taskId, String exportPath, String databaseName, String schemaName,
            List<String> tableNames, boolean containData, ITaskAsyncCall asyncCall, Context context,
            ExportFileTarget exportFileTarget) {
        ExportFileTarget target = exportFileTarget == null
                ? prepareSqlExportTarget(exportPath, databaseName, schemaName, tableNames, null, Boolean.FALSE)
                : exportFileTarget;
        return new AsyncContext(asyncCall, context, target.getStagingFile(), containData, target);
    }

    @Override
    public ExportAsyncContext createOtherExportContext(Long taskId, String exportPath, String databaseName,
            String schemaName, List<String> tableNames, String exportType, Boolean containsHeader,
            ITaskAsyncCall asyncCall, Context context) {
        return createOtherExportContext(taskId, exportPath, databaseName, schemaName, tableNames, exportType,
                containsHeader, asyncCall, context, null, Boolean.FALSE);
    }

    @Override
    public ExportAsyncContext createOtherExportContext(Long taskId, String exportPath, String databaseName,
            String schemaName, List<String> tableNames, String exportType, Boolean containsHeader,
            ITaskAsyncCall asyncCall, Context context, String exportFileName, Boolean overwriteExistingFile) {
        ExportFileTarget exportFileTarget = prepareOtherExportTarget(exportPath, databaseName, schemaName, tableNames,
                exportType, exportFileName, overwriteExistingFile);
        return createOtherExportContext(taskId, exportPath, databaseName, schemaName, tableNames, exportType,
                containsHeader, asyncCall, context, exportFileTarget);
    }

    @Override
    public ExportFileTarget prepareOtherExportTarget(String exportPath, String databaseName, String schemaName,
            List<String> tableNames, String exportType, String exportFileName, Boolean overwriteExistingFile) {
        validateExportTableNames(tableNames);
        String normalizedExportType = normalizeExportType(exportType);
        String suffix = tableNames.size() == 1 ? EXPORT_TYPE_SUFFIXES.get(normalizedExportType) : "zip";
        String defaultFileName = tableNames.size() == 1 ? tableNames.get(0)
                : resolveTaskName(databaseName, schemaName, tableNames);
        return prepareExportTarget(exportPath, exportFileName, defaultFileName, suffix, overwriteExistingFile);
    }

    @Override
    public ExportAsyncContext createOtherExportContext(Long taskId, String exportPath, String databaseName,
            String schemaName, List<String> tableNames, String exportType, Boolean containsHeader,
            ITaskAsyncCall asyncCall, Context context, ExportFileTarget exportFileTarget) {
        String normalizedExportType = normalizeExportType(exportType);
        ExportFileTarget target = exportFileTarget == null
                ? prepareOtherExportTarget(exportPath, databaseName, schemaName, tableNames, normalizedExportType, null,
                        Boolean.FALSE)
                : exportFileTarget;
        return new ExportAsyncContext(asyncCall, context, target.getStagingFile(), target, normalizedExportType,
                tableNames, "single", containsHeader);
    }

    @Override
    public ImportAsyncContext createImportContext(Long taskId, String importType, String tableName, String fileName,
            ITaskAsyncCall asyncCall, Context context) {
        return new ImportAsyncContext(asyncCall, context, importType, tableName, new File(fileName));
    }

    private ExportFileTarget prepareExportTarget(String path, String exportFileName, String defaultFileName, String suffix,
            Boolean overwriteExistingFile) {
        boolean hasCustomFileName = StringUtils.isNotBlank(exportFileName);
        String normalizedFileName = hasCustomFileName
                ? normalizeExportFileName(exportFileName, suffix)
                : defaultExportFileName(defaultFileName, suffix);
        File directory = createExportDirectory(path);
        Path directoryPath = directory.toPath().toAbsolutePath().normalize();
        boolean overwrite = hasCustomFileName && Boolean.TRUE.equals(overwriteExistingFile);
        Path targetFile = resolveTargetFile(directoryPath, normalizedFileName);
        if (overwrite && Files.isDirectory(targetFile)) {
            throw new IllegalArgumentException("Export file target must not be a directory");
        }
        try {
            Path stagingDirectory = Files.createTempDirectory(directoryPath, ".chat2db-export-");
            Path stagingFile = stagingDirectory.resolve(normalizedFileName);
            return new ExportFileTarget(targetFile.toFile(), stagingFile.toFile(), stagingDirectory.toFile(), !overwrite);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare export file", e);
        }
    }

    private String normalizeExportFileName(String exportFileName, String suffix) {
        String normalizedFileName = exportFileName.trim();
        validateFileName(normalizedFileName);
        int extensionIndex = normalizedFileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? normalizedFileName.substring(0, extensionIndex) : normalizedFileName;
        if (StringUtils.isBlank(baseName)) {
            throw new IllegalArgumentException("Export file name must not be blank");
        }
        return baseName + "." + suffix;
    }

    private String defaultExportFileName(String defaultFileName, String suffix) {
        String baseName = StringUtils.defaultIfBlank(StringUtils.trim(defaultFileName), DEFAULT_TASK_NAME);
        try {
            validateFileName(baseName);
        } catch (IllegalArgumentException ignored) {
            baseName = DEFAULT_TASK_NAME;
        }
        return baseName + "." + suffix;
    }

    private void validateFileName(String fileName) {
        if (fileName.contains("/") || fileName.contains("\\") || ".".equals(fileName) || "..".equals(fileName)
                || fileName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Export file name must not contain a path");
        }
    }

    private Path resolveTargetFile(Path directory, String fileName) {
        Path targetFile = directory.resolve(fileName).normalize();
        if (!directory.equals(targetFile.getParent())) {
            throw new IllegalArgumentException("Export file name must not contain a path");
        }
        return targetFile;
    }

    private void validateExportTableNames(List<String> tableNames) {
        if (CollectionUtils.isEmpty(tableNames) || tableNames.stream().anyMatch(StringUtils::isBlank)) {
            throw new IllegalArgumentException("Export table names must not be empty");
        }
    }

    private String normalizeExportType(String exportType) {
        String normalizedExportType = StringUtils.trimToEmpty(exportType).toUpperCase(Locale.ROOT);
        if (!EXPORT_TYPE_SUFFIXES.containsKey(normalizedExportType)) {
            throw new IllegalArgumentException("Unsupported export type: " + exportType);
        }
        return normalizedExportType;
    }

    private File createExportDirectory(String path) {
        File directory = new File(StringUtils.defaultIfBlank(path, defaultExportPath()));
        if (directory.exists() && !directory.isDirectory()) {
            throw new IllegalArgumentException("Export path must be a directory");
        }
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Unable to create export directory");
        }
        return directory;
    }
}
