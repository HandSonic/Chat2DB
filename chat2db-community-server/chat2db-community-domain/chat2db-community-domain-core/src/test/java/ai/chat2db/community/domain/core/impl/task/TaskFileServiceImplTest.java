package ai.chat2db.community.domain.core.impl.task;

import ai.chat2db.community.domain.api.model.task.ExportAsyncContext;
import ai.chat2db.community.domain.api.model.async.AsyncContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskFileServiceImplTest {

    @TempDir
    Path tempDir;

    private final TaskFileServiceImpl service = new TaskFileServiceImpl();

    @Test
    void usesCustomFileNameWithTheSelectedExportExtension() {
        ExportAsyncContext context = createContext("orders-staging-2026-07-27.sql", false);

        try {
            assertEquals(tempDir.resolve("orders-staging-2026-07-27.csv").toFile(), context.getOutputFile());
            assertFalse(context.getWriteFile().toPath().startsWith(tempDir.resolve("orders-staging-2026-07-27.csv")),
                    "the exporter must write to a task-local staging file");
        } finally {
            context.finish();
        }
    }

    @Test
    void renamesCustomFileWhenTheTargetAlreadyExists() throws Exception {
        Path existingFile = tempDir.resolve("orders.csv");
        Files.writeString(existingFile, "existing export");

        ExportAsyncContext context = createContext("orders.csv", false);
        Path renamedFile = tempDir.resolve("orders_1.csv");
        assertFalse(Files.exists(renamedFile), "rename mode must not reserve a visible final file");

        context.write("renamed export");
        context.finish();

        assertEquals(renamedFile.toFile(), context.getOutputFile());
        assertEquals("existing export", Files.readString(existingFile));
        assertEquals("renamed export\n", Files.readString(renamedFile));
    }

    @Test
    void overwritesCustomFileOnlyAfterSuccessfulCompletion() throws Exception {
        Path existingFile = tempDir.resolve("orders.csv");
        Files.writeString(existingFile, "existing export");

        ExportAsyncContext context = createContext("orders.csv", true);

        try {
            assertEquals(existingFile.toFile(), context.getOutputFile());
            assertEquals("existing export", Files.readString(existingFile),
                    "opening the export context must not truncate an existing target");
            context.write("replacement export");
        } finally {
            context.finish();
        }

        assertEquals("replacement export\n", Files.readString(existingFile));
    }

    @Test
    void failedOverwriteKeepsTheExistingFileAndCleansTheStagingOutput() throws Exception {
        Path existingFile = tempDir.resolve("orders.csv");
        Files.writeString(existingFile, "existing export");
        ExportAsyncContext context = createContext("orders.csv", true);
        Path stagingFile = context.getWriteFile().toPath();

        context.write("partial export");
        context.error("export failed");
        context.finish();

        assertEquals("existing export", Files.readString(existingFile));
        assertFalse(Files.exists(stagingFile));
    }

    @Test
    void cancellationKeepsTheExistingFile() throws Exception {
        Path existingFile = tempDir.resolve("orders.csv");
        Files.writeString(existingFile, "existing export");
        ExportAsyncContext context = createContext("orders.csv", true);

        context.write("partial export");
        context.stop();
        context.finish();

        assertEquals("existing export", Files.readString(existingFile));
    }

    @Test
    void cancellationDoesNotDeleteAForeignFileCreatedAtTheRequestedPath() throws Exception {
        ExportAsyncContext context = createContext("orders.csv", false);
        Path requestedTarget = context.getOutputFile().toPath();
        assertFalse(Files.exists(requestedTarget), "preparing rename mode must not create a final-file reservation");
        Files.writeString(requestedTarget, "foreign export");

        context.stop();
        context.finish();

        assertEquals("foreign export", Files.readString(requestedTarget));
    }

    @Test
    void renameRetriesWhenAnotherProcessCreatesTheRequestedTargetDuringExport() throws Exception {
        ExportAsyncContext context = createContext("orders.csv", false);
        Path requestedTarget = context.getOutputFile().toPath();
        context.write("our export");
        Files.writeString(requestedTarget, "foreign export");

        context.finish();

        Path renamedTarget = tempDir.resolve("orders_1.csv");
        assertEquals(renamedTarget.toFile(), context.getOutputFile());
        assertEquals("foreign export", Files.readString(requestedTarget));
        assertEquals("our export\n", Files.readString(renamedTarget));
    }

    @Test
    void rejectsAFileNameThatContainsAPath() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> createContext("../orders.csv", false));

        assertTrue(exception.getMessage().contains("must not contain a path"));
    }

    @Test
    void rejectsAHostileExportTypeBeforeItCanBecomeAFileSuffix() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.createOtherExportContext(1L, tempDir.toString(), "orders_db", null, List.of("orders"),
                        "CSV/../../outside", true, null, null, "orders.csv", false));

        assertTrue(exception.getMessage().contains("Unsupported export type"));
    }

    @Test
    void publishesUniqueNamesAtomicallyForConcurrentRenameExports() throws Exception {
        Files.writeString(tempDir.resolve("orders.csv"), "existing export");
        ExecutorService executor = Executors.newFixedThreadPool(6);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    ExportAsyncContext context = createContext("orders.csv", false);
                    context.write("concurrent export");
                    context.finish();
                    return context.getOutputFile().getName();
                }));
            }
            Set<String> outputNames = new HashSet<>();
            for (Future<String> future : futures) {
                outputNames.add(future.get());
            }
            assertEquals(6, outputNames.size());
            assertTrue(outputNames.stream().allMatch(name -> name.matches("orders_[1-6]\\.csv")));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void usesCustomSqlFileNameAndNormalizesItsExtension() {
        AsyncContext context = service.createSqlExportContext(1L, tempDir.toString(), "orders_db", null,
                List.of("orders"), false, null, null, "orders-staging-2026-07-27.csv", false);

        try {
            assertEquals(tempDir.resolve("orders-staging-2026-07-27.sql").toFile(), context.getOutputFile());
        } finally {
            context.finish();
        }
    }

    private ExportAsyncContext createContext(String exportFileName, boolean overwriteExistingFile) {
        return service.createOtherExportContext(1L, tempDir.toString(), "orders_db", null, List.of("orders"), "CSV",
                true, null, null, exportFileName, overwriteExistingFile);
    }
}
