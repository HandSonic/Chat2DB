package ai.chat2db.community.domain.api.model.async;

import ai.chat2db.community.domain.api.service.task.ITaskAsyncCall;
import ai.chat2db.community.tools.model.Context;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for code-review finding core:domain-api-2:
 * the finish flag (and the progress/info/error state read by the callback
 * thread) must be safely published so the polling thread terminates.
 */
class AsyncContextTest {

    @Test
    void sharedStateFieldsAreVolatile() throws Exception {
        assertTrue(Modifier.isVolatile(AsyncContext.class.getDeclaredField("finish").getModifiers()),
                "finish must be volatile so the polling thread observes stop()/finish()");
        assertTrue(Modifier.isVolatile(AsyncContext.class.getDeclaredField("progress").getModifiers()),
                "progress is written by the task thread and read by the callback thread");
        assertTrue(Modifier.isVolatile(AsyncContext.class.getDeclaredField("info").getModifiers()),
                "info is reassigned in callUpdate() and appended by the task thread");
        assertTrue(Modifier.isVolatile(AsyncContext.class.getDeclaredField("error").getModifiers()),
                "error is reassigned in callUpdate() and appended by the task thread");
    }

    @Test
    void backgroundThreadTerminatesAfterFinish() throws Exception {
        AtomicInteger updates = new AtomicInteger();
        ITaskAsyncCall call = map -> updates.incrementAndGet();
        AsyncContext asyncContext = new AsyncContext(call, new Context(), null, false);

        // let the callback thread enter its poll loop (first sleep is 2s)
        Thread.sleep(3000L);
        assertTrue(updates.get() >= 1, "callback thread should have reported progress");

        asyncContext.finish();

        // wait longer than the maximum poll sleep (2s * 5 = 10s) so the thread
        // has had a chance to observe the finish flag, then verify no more updates arrive
        Thread.sleep(11000L);
        int settled = updates.get();
        Thread.sleep(3000L);
        assertEquals(settled, updates.get(),
                "background callback thread kept running after finish()");
    }
}
