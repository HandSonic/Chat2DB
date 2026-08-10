package ai.chat2db.community.domain.core.impl.task;

import ai.chat2db.community.domain.api.model.request.task.TaskOtherFileExportRequest;
import ai.chat2db.community.domain.api.model.request.task.TaskRecordUpdateRequest;
import ai.chat2db.community.domain.api.model.request.task.TaskSqlFileExportRequest;
import ai.chat2db.community.domain.api.model.async.AsyncContext;
import ai.chat2db.community.domain.api.service.task.ITaskExecutionService;
import ai.chat2db.community.domain.api.service.task.ITaskRecordService;
import ai.chat2db.community.domain.api.service.task.ITaskSchedulerService;
import ai.chat2db.community.tools.model.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskTransferServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void invalidOtherExportFileNameDoesNotCreateATaskRecord() {
        AtomicInteger createTaskCalls = new AtomicInteger();
        TaskOtherFileExportRequest request = new TaskOtherFileExportRequest();
        request.setExportPath(tempDir.toString());
        request.setTableNames(List.of("orders"));
        request.setExportType("CSV");
        request.setExportFileName("../orders.csv");

        assertThrows(IllegalArgumentException.class, () -> transferService(createTaskCalls).exportOtherFile(request));

        assertEquals(0, createTaskCalls.get());
    }

    @Test
    void invalidSqlScopeDoesNotCreateATaskRecord() {
        AtomicInteger createTaskCalls = new AtomicInteger();
        TaskSqlFileExportRequest request = new TaskSqlFileExportRequest();
        request.setExportPath(tempDir.toString());
        request.setTableNames(List.of("orders"));
        request.setScope("NOT_A_SCOPE");

        assertThrows(IllegalArgumentException.class, () -> transferService(createTaskCalls).exportSqlFile(request));

        assertEquals(0, createTaskCalls.get());
    }

    @Test
    void schedulingFailureKeepsAnOverwriteTargetAndMarksTheTaskAsError() throws Exception {
        Path existingFile = tempDir.resolve("orders.csv");
        Files.writeString(existingFile, "existing export");
        AtomicReference<TaskRecordUpdateRequest> updateRequest = new AtomicReference<>();
        ITaskRecordService taskRecordService = (ITaskRecordService) Proxy.newProxyInstance(
                TaskTransferServiceImplTest.class.getClassLoader(), new Class<?>[]{ITaskRecordService.class},
                (proxy, method, arguments) -> {
                    if ("createTask".equals(method.getName())) {
                        return 1L;
                    }
                    if ("updateTask".equals(method.getName())) {
                        updateRequest.set((TaskRecordUpdateRequest) arguments[0]);
                    }
                    return defaultValue(method.getReturnType());
                });
        ITaskExecutionService taskExecutionService = new ITaskExecutionService() {
            @Override
            public Runnable withCurrentConnectionContext(Context context, Runnable runnable) {
                return runnable;
            }

            @Override
            public Runnable withConnectionProfile(Context context,
                    ai.chat2db.community.domain.api.model.runtime.ConnectionProfile profile, Runnable runnable) {
                return runnable;
            }
        };
        ITaskSchedulerService taskSchedulerService = new ITaskSchedulerService() {
            @Override
            public ai.chat2db.community.domain.api.service.task.ITaskAsyncCall asyncCall(Long taskId) {
                return update -> {
                    // The failure path publishes STOP before TaskTransfer records ERROR.
                };
            }

            @Override
            public void submit(Long taskId, AsyncContext asyncContext, Runnable runnable) {
                throw new IllegalStateException("scheduler unavailable");
            }

            @Override
            public boolean cancel(Long taskId) {
                return false;
            }
        };
        TaskTransferServiceImpl service = new TaskTransferServiceImpl(taskExecutionService, null, null, null,
                taskRecordService, new TaskFileServiceImpl(), taskSchedulerService);
        TaskOtherFileExportRequest request = new TaskOtherFileExportRequest();
        request.setExportPath(tempDir.toString());
        request.setTableNames(List.of("orders"));
        request.setExportType("CSV");
        request.setExportFileName("orders.csv");
        request.setOverwriteExistingFile(Boolean.TRUE);

        assertThrows(IllegalStateException.class, () -> service.exportOtherFile(request));

        assertEquals("existing export", Files.readString(existingFile));
        assertEquals("ERROR", updateRequest.get().getTaskStatus());
    }

    private TaskTransferServiceImpl transferService(AtomicInteger createTaskCalls) {
        ITaskRecordService taskRecordService = (ITaskRecordService) Proxy.newProxyInstance(
                TaskTransferServiceImplTest.class.getClassLoader(), new Class<?>[]{ITaskRecordService.class},
                (proxy, method, arguments) -> {
                    if ("createTask".equals(method.getName())) {
                        createTaskCalls.incrementAndGet();
                        return 1L;
                    }
                    return defaultValue(method.getReturnType());
                });
        return new TaskTransferServiceImpl(null, null, null, null, taskRecordService, new TaskFileServiceImpl(), null);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
