package ai.chat2db.community.web.api.converter.task;

import ai.chat2db.community.domain.api.enums.ExportScopeTypeEnum;
import ai.chat2db.community.domain.api.model.request.task.TaskSqlFileExportRequest;
import ai.chat2db.community.web.api.model.request.task.SqlFileExportRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskWebConverterTest {

    private final TaskWebConverter converter = new TaskWebConverter();

    @Test
    void mapsTheSqlExportFileNameAndConflictDecision() {
        SqlFileExportRequest request = new SqlFileExportRequest();
        request.setDatabaseName("orders");
        request.setSchemaName("public");
        request.setTableNames(List.of("orders"));
        request.setScope(ExportScopeTypeEnum.ALL);
        request.setExportPath("C:/exports");
        request.setExportFileName("orders-staging-2026-07-27.sql");
        request.setOverwriteExistingFile(Boolean.TRUE);

        TaskSqlFileExportRequest result = converter.sqlFileExport2param(request);

        assertEquals("orders-staging-2026-07-27.sql", result.getExportFileName());
        assertTrue(result.getOverwriteExistingFile());
        assertEquals("ALL", result.getScope());
    }
}
