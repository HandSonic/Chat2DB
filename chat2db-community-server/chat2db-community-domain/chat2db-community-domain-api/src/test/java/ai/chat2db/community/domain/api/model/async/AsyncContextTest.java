package ai.chat2db.community.domain.api.model.async;

import ai.chat2db.community.domain.api.service.task.ITaskAsyncCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for code-review finding core:domain-api-2:
 * the finish flag (and the progress/info/error state read by the callback
 * thread) must be safely published so the polling thread terminates.
 */
class AsyncContextTest {

    @TempDir
    Path tempDir;

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
        assertTrue(Modifier.isVolatile(AsyncContext.class.getDeclaredField("state").getModifiers()),
                "state is written by cancellation and read by the task and callback threads");
    }

    @Test
    void stopRemainsTerminalWhenTaskFinallyFinishes() {
        List<Map<String, Object>> updates = new ArrayList<>();
        ITaskAsyncCall call = update -> updates.add(new HashMap<>(update));
        AsyncContext context = new AsyncContext(
                call, null, tempDir.resolve("cancelled.sql").toFile(), true);

        context.stop();
        context.finish();

        assertTrue(context.isStopped());
        assertEquals(1, updates.size(), "finish must not publish another terminal update after STOP");
        assertEquals("STOP", updates.get(0).get("status"));
        assertFalse(updates.get(0).containsKey("downloadUrl"),
                "cancelled exports must not expose a successful download");
    }

    @Test
    void successfulFinishPublishesDownload() {
        List<Map<String, Object>> updates = new ArrayList<>();
        ITaskAsyncCall call = update -> updates.add(new HashMap<>(update));
        Path output = tempDir.resolve("finished.sql");
        AsyncContext context = new AsyncContext(call, null, output.toFile(), true);

        context.finish();

        assertEquals(1, updates.size());
        assertEquals("FINISHED", updates.get(0).get("status"));
        assertEquals(100, updates.get(0).get("progress"));
        assertEquals(output.toFile().getAbsolutePath(), updates.get(0).get("downloadUrl"));
    }

}
