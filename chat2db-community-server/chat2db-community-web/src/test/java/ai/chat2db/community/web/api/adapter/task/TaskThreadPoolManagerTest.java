package ai.chat2db.community.web.api.adapter.task;

import ai.chat2db.community.domain.api.model.async.AsyncContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskThreadPoolManagerTest {

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

            TaskThreadPoolManager.cancelTask(taskId);
            task.join(5000);
        } finally {
            blocker.countDown();
        }

        assertFalse(task.isAlive(), "cancelled task must terminate after interruption");
        assertTrue(interrupted.get(), "cancel must interrupt a blocking task");
        assertFalse(ranToCompletion.get(), "cancelled task must not execute its completion side effect");
        assertTrue(asyncContext.isStopped(), "cancelled task status must remain STOP");
        Field field = TaskThreadPoolManager.class.getDeclaredField("taskMap");
        field.setAccessible(true);
        Map<Long, TaskThread> taskMap = (Map<Long, TaskThread>) field.get(null);
        assertFalse(taskMap.containsKey(taskId), "cancelled task must not linger in taskMap");
    }

    @Test
    void cancelTaskOnUnknownTaskIdIsNoOp() {
        TaskThreadPoolManager.cancelTask(990002L);
    }
}
