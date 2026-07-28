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
    void cancelTaskUsesCooperativeCancelWithoutStoppingThread() throws Exception {
        Long taskId = 990001L;
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean ranToCompletion = new AtomicBoolean(false);
        AsyncContext asyncContext = new AsyncContext(null, null, null, false);
        TaskThread task = new TaskThread(null, asyncContext, taskId, () -> {
            started.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ranToCompletion.set(true);
        });

        TaskThreadPoolManager.submitTask(taskId, task);
        assertTrue(started.await(5, TimeUnit.SECONDS), "task thread did not start");

        TaskThreadPoolManager.cancelTask(taskId);
        release.countDown();
        task.join(5000);

        assertTrue(ranToCompletion.get(), "task thread must run to completion; Thread.stop() must not be used");
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
