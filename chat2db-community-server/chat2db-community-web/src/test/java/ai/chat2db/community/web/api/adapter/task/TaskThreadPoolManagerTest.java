package ai.chat2db.community.web.api.adapter.task;

import ai.chat2db.community.domain.api.model.async.AsyncContext;
import ai.chat2db.community.domain.api.model.async.ExportFileTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskThreadPoolManagerTest {

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void cancelTaskInterruptsTaskAndPreventsCompletionSideEffect() throws Exception {
        Long taskId = 990001L;
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        AtomicBoolean ranToCompletion = new AtomicBoolean(false);
        AsyncContext asyncContext = new AsyncContext(null, null, null, false);
        TaskThread task = new TaskThread(null, asyncContext, taskId, () -> {
            started.countDown();
            try {
                blocker.await(10, TimeUnit.SECONDS);
                ranToCompletion.set(true);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        try {
            TaskThreadPoolManager.submitTask(taskId, task);
            assertTrue(started.await(5, TimeUnit.SECONDS), "task thread did not start");

            assertTrue(TaskThreadPoolManager.cancelTask(taskId));
            task.join(5000);
        } finally {
            blocker.countDown();
        }

        assertFalse(task.isAlive(), "cancelled task must terminate after interruption");
        assertTrue(interrupted.get(), "cancel must interrupt a blocking task");
        assertFalse(ranToCompletion.get(), "cancelled task must not execute its completion side effect");
        assertTrue(asyncContext.isStopped(), "cancelled task status must remain STOP");
        assertFalse(taskMap().containsKey(taskId), "cancelled task must not linger in taskMap");
    }

    @Test
    void cancelTaskOnUnknownTaskIdIsNoOp() {
        assertFalse(TaskThreadPoolManager.cancelTask(990002L));
    }

    @Test
    void cancelTaskStopsCooperativeWorkLoop() throws Exception {
        Long taskId = 990003L;
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger sideEffects = new AtomicInteger();
        AsyncContext asyncContext = new AsyncContext(null, null, null, false);
        TaskThread task = new TaskThread(null, asyncContext, taskId, () -> {
            while (true) {
                asyncContext.checkCancelled();
                sideEffects.incrementAndGet();
                started.countDown();
                Thread.onSpinWait();
            }
        });

        try {
            TaskThreadPoolManager.submitTask(taskId, task);
            assertTrue(started.await(5, TimeUnit.SECONDS), "task thread did not start");
            assertTrue(TaskThreadPoolManager.cancelTask(taskId));
            task.join(5000);
        } finally {
            if (task.isAlive()) {
                task.cancel();
                task.join(5000);
            }
        }

        assertFalse(task.isAlive(), "cooperative task must terminate after cancellation");
        int countAfterStop = sideEffects.get();
        Thread.sleep(20);
        assertTrue(countAfterStop > 0, "test task did not execute any work");
        assertTrue(asyncContext.isStopped());
        assertEquals(countAfterStop, sideEffects.get(), "task produced side effects after STOP");
    }

    @Test
    void cancelTaskLeavesAnErrorCallbackPendingForTaskRecordService() throws Exception {
        Long taskId = 990005L;
        CountDownLatch errorUpdateStarted = new CountDownLatch(1);
        CountDownLatch releaseErrorUpdate = new CountDownLatch(1);
        Path outputParent = tempDir.resolve("not-a-directory");
        Files.writeString(outputParent, "not a directory");
        Path stagingDirectory = Files.createTempDirectory(tempDir, ".chat2db-export-");
        Path stagingFile = stagingDirectory.resolve("orders.csv");
        ExportFileTarget target = new ExportFileTarget(outputParent.resolve("orders.csv").toFile(),
                stagingFile.toFile(), stagingDirectory.toFile(), true);
        AsyncContext context = new AsyncContext(update -> {
            if ("ERROR".equals(update.get("status"))) {
                errorUpdateStarted.countDown();
                await(releaseErrorUpdate);
            }
        }, null, stagingFile.toFile(), true, target);
        TaskThread task = new TaskThread(null, context, taskId, () -> context.write("export data"));

        try {
            TaskThreadPoolManager.submitTask(taskId, task);
            assertTrue(errorUpdateStarted.await(5, TimeUnit.SECONDS), "ERROR callback did not start");

            assertFalse(TaskThreadPoolManager.cancelTask(taskId),
                    "a terminal context must not report that STOP was accepted");
            assertTrue(TaskThreadPoolManager.isTerminalUpdatePending(taskId));
            assertFalse(context.isStopped());
            assertTrue(task.isAlive(), "terminal callback must not be interrupted");
        } finally {
            releaseErrorUpdate.countDown();
            task.join(5000);
        }

        assertFalse(task.isAlive());
        assertFalse(taskMap().containsKey(taskId));
    }

    @Test
    void cancelTaskLeavesAPublishedFinishedCallbackPendingForTaskRecordService() throws Exception {
        Long taskId = 990006L;
        CountDownLatch finishedUpdateStarted = new CountDownLatch(1);
        CountDownLatch releaseFinishedUpdate = new CountDownLatch(1);
        Path output = tempDir.resolve("orders.csv");
        Path stagingDirectory = Files.createTempDirectory(tempDir, ".chat2db-export-");
        Path stagingFile = stagingDirectory.resolve("orders.csv");
        ExportFileTarget target = new ExportFileTarget(output.toFile(), stagingFile.toFile(),
                stagingDirectory.toFile(), false);
        AsyncContext context = new AsyncContext(update -> {
            if ("FINISHED".equals(update.get("status"))) {
                finishedUpdateStarted.countDown();
                await(releaseFinishedUpdate);
            }
        }, null, stagingFile.toFile(), true, target);
        TaskThread task = new TaskThread(null, context, taskId, () -> context.write("export data"));

        try {
            TaskThreadPoolManager.submitTask(taskId, task);
            assertTrue(finishedUpdateStarted.await(5, TimeUnit.SECONDS), "FINISHED callback did not start");

            assertFalse(TaskThreadPoolManager.cancelTask(taskId),
                    "a published terminal context must not accept cancellation");
            assertTrue(TaskThreadPoolManager.isTerminalUpdatePending(taskId));
            assertFalse(context.isStopped());
            assertTrue(task.isAlive(), "terminal callback must not be interrupted");
            assertEquals("export data\n", Files.readString(output));
        } finally {
            releaseFinishedUpdate.countDown();
            task.join(5000);
        }

        assertFalse(task.isAlive());
        assertFalse(taskMap().containsKey(taskId));
    }

    @Test
    void interruptedTaskDiscardsStagedPartialOutputAndPublishesStop() throws Exception {
        Long taskId = 990007L;
        AtomicReference<Map<String, Object>> update = new AtomicReference<>();
        Path output = tempDir.resolve("orders.csv");
        Files.writeString(output, "original export");
        Path stagingDirectory = Files.createTempDirectory(tempDir, ".chat2db-export-");
        Path stagingFile = stagingDirectory.resolve("orders.csv");
        ExportFileTarget target = new ExportFileTarget(output.toFile(), stagingFile.toFile(),
                stagingDirectory.toFile(), false);
        AsyncContext context = new AsyncContext(update::set, null, stagingFile.toFile(), true, target);
        TaskThread task = new TaskThread(null, context, taskId, () -> {
            context.write("partial export");
            Thread.currentThread().interrupt();
            context.checkCancelled();
        });

        try {
            TaskThreadPoolManager.submitTask(taskId, task);
            task.join(5000);
        } finally {
            if (task.isAlive()) {
                task.cancel();
                task.join(5000);
            }
        }

        assertFalse(task.isAlive());
        assertTrue(context.isStopped());
        assertEquals("original export", Files.readString(output));
        assertFalse(Files.exists(stagingDirectory));
        assertEquals("STOP", update.get().get("status"));
        assertEquals("", update.get().get("downloadUrl"));
    }

    @Test
    void cancellationExceptionDoesNotMaskAPriorExporterError() throws Exception {
        Long taskId = 990008L;
        AtomicReference<Map<String, Object>> update = new AtomicReference<>();
        Path output = tempDir.resolve("orders.csv");
        Files.writeString(output, "original export");
        Path stagingDirectory = Files.createTempDirectory(tempDir, ".chat2db-export-");
        Path stagingFile = stagingDirectory.resolve("orders.csv");
        ExportFileTarget target = new ExportFileTarget(output.toFile(), stagingFile.toFile(),
                stagingDirectory.toFile(), false);
        AsyncContext context = new AsyncContext(update::set, null, stagingFile.toFile(), true, target);
        TaskThread task = new TaskThread(null, context, taskId, () -> {
            context.write("partial export");
            context.error("exporter failed");
            Thread.currentThread().interrupt();
            context.checkCancelled();
        });

        try {
            TaskThreadPoolManager.submitTask(taskId, task);
            task.join(5000);
        } finally {
            if (task.isAlive()) {
                task.cancel();
                task.join(5000);
            }
        }

        assertFalse(task.isAlive());
        assertFalse(context.isStopped());
        assertEquals("original export", Files.readString(output));
        assertFalse(Files.exists(stagingDirectory));
        assertEquals("ERROR", update.get().get("status"));
        assertEquals("", update.get().get("downloadUrl"));
        assertTrue(String.valueOf(update.get().get("error")).contains("exporter failed"));
    }

    @Test
    void completedOldTaskDoesNotRemoveReplacementWithSameId() throws Exception {
        Long taskId = 990004L;
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        CountDownLatch replacementStarted = new CountDownLatch(1);
        CountDownLatch releaseReplacement = new CountDownLatch(1);
        TaskThread oldTask = new TaskThread(null, new AsyncContext(null, null, null, false), taskId, () -> {
            oldStarted.countDown();
            await(releaseOld);
        });
        TaskThread replacement = new TaskThread(
                null, new AsyncContext(null, null, null, false), taskId, () -> {
                    replacementStarted.countDown();
                    await(releaseReplacement);
                });

        try {
            TaskThreadPoolManager.submitTask(taskId, oldTask);
            assertTrue(oldStarted.await(5, TimeUnit.SECONDS), "old task did not start");
            TaskThreadPoolManager.submitTask(taskId, replacement);
            assertTrue(replacementStarted.await(5, TimeUnit.SECONDS), "replacement task did not start");

            releaseOld.countDown();
            oldTask.join(5000);

            assertFalse(oldTask.isAlive());
            assertSame(replacement, taskMap().get(taskId));
        } finally {
            releaseOld.countDown();
            releaseReplacement.countDown();
            oldTask.join(5000);
            replacement.join(5000);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, TaskThread> taskMap() throws Exception {
        Field field = TaskThreadPoolManager.class.getDeclaredField("taskMap");
        field.setAccessible(true);
        return (Map<Long, TaskThread>) field.get(null);
    }
}
