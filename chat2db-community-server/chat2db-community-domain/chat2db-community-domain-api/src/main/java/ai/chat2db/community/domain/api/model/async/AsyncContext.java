package ai.chat2db.community.domain.api.model.async;

import ai.chat2db.community.domain.api.service.task.ITaskAsyncCall;
import ai.chat2db.community.domain.api.service.db.ISqlExecutionStatementListener;
import ai.chat2db.community.tools.model.Context;
import ai.chat2db.community.tools.util.ContextUtils;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.PrintWriter;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class AsyncContext implements ISqlExecutionStatementListener {

    private static final int TERMINAL_UPDATE_MAX_ATTEMPTS = 3;

    private enum State {
        RUNNING,
        FINISHED,
        ERROR,
        STOP
    }

    private volatile File writeFile;

    private final ExportFileTarget exportFileTarget;

    private volatile boolean outputPublished;

    private volatile boolean errorOccurred;

    protected PrintWriter writer;

    protected boolean containsData;

    protected ITaskAsyncCall call;

    protected volatile boolean finish;

    protected volatile Integer progress;

    private volatile State state = State.RUNNING;

    private final Object callbackLock = new Object();

    private final Object messageLock = new Object();

    private boolean terminalUpdatePublished;

    private final AtomicReference<Statement> currentStatement = new AtomicReference<>();

    private volatile StringBuffer info = new StringBuffer();

    private volatile StringBuffer error = new StringBuffer();

    public AsyncContext(ITaskAsyncCall call, Context context, File writeFile, boolean containsData) {
        this(call, context, writeFile, containsData, null);
    }

    /**
     * Creates an export context which writes to a staged file and publishes it
     * only after the task completes successfully. The extra argument keeps the
     * legacy four-argument constructor unambiguous for callers that pass null.
     */
    public AsyncContext(ITaskAsyncCall call, Context context, File writeFile, boolean containsData,
            ExportFileTarget exportFileTarget) {
        this.call = call;
        this.writeFile = writeFile;
        this.exportFileTarget = exportFileTarget;
        this.outputPublished = exportFileTarget == null && writeFile != null;
        this.progress = 5;
        this.containsData = containsData;
        try {
            createWriter();
        } catch (RuntimeException e) {
            if (exportFileTarget != null) {
                exportFileTarget.discard();
            }
            throw e;
        }
        appendInfo(DateUtil.formatDateTime(new Date()) + ":start------");
        asyncCallBack(context);
    }

    public File getWriteFile() {
        return writeFile;
    }

    /**
     * Returns the user-visible output path. For rename-mode exports before
     * publication this is the requested initial candidate; after publication
     * it is the uniquely selected final path. For legacy direct-output
     * contexts this is the same file returned by {@link #getWriteFile()}.
     */
    public File getOutputFile() {
        return exportFileTarget == null ? writeFile : exportFileTarget.getTargetFile();
    }

    public boolean isContainsData() {
        return containsData;
    }

    public void setProgress(Integer progress) {
        checkCancelled();
        if (progress == null) {
            return;
        }
        if (progress >= 100) {
            progress = 99;
        }
        this.progress = progress;
    }

    public void info(String message) {
        checkCancelled();
        appendInfo(message);
    }

    public void error(String message) {
        if (isStopped()) {
            return;
        }
        errorOccurred = true;
        synchronized (messageLock) {
            error.append(message).append("\n");
            info.append(message).append("\n");
        }
    }

    public boolean hasError() {
        return errorOccurred;
    }

    public boolean stop() {
        synchronized (this) {
            if (state == State.STOP || state == State.ERROR || terminalUpdatePublished
                    || (state == State.FINISHED && exportFileTarget != null && outputPublished)) {
                return false;
            }
            state = State.STOP;
            finish = true;
        }
        cancelStatement(currentStatement.get());
        return true;
    }

    public boolean isStopped() {
        return state == State.STOP;
    }

    /**
     * Returns whether a terminal state has been selected but its callback has
     * not yet been persisted. Callers use this to avoid overwriting an
     * in-flight FINISHED, ERROR, or STOP update with a late cancellation.
     */
    public boolean isTerminalUpdatePending() {
        synchronized (this) {
            return state != State.RUNNING && !terminalUpdatePublished;
        }
    }

    public void checkCancelled() {
        if (isStopped() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Task was cancelled");
        }
    }

    public void finish() {
        synchronized (this) {
            finish = true;
            if (closeWriter()) {
                error("Unable to write export file");
            }
            boolean publicationFailed = finalizeExportFile();
            if (state == State.RUNNING) {
                boolean terminalError = publicationFailed || errorOccurred;
                state = terminalError ? State.ERROR : State.FINISHED;
                this.progress = 100;
                String message = DateUtil.formatDateTime(new Date()) + " "
                        + (terminalError ? "export failed. " : "finish. ");
                if (outputPublished && getOutputFile() != null) {
                    message += "File path:" + getOutputFile().getAbsolutePath();
                }
                appendInfo(message);
            }
        }
        publishTerminalUpdate();
    }

    private boolean closeWriter() {
        if (writer == null) {
            return false;
        }
        PrintWriter closingWriter = writer;
        writer = null;
        closingWriter.flush();
        boolean writerFailed = closingWriter.checkError();
        closingWriter.close();
        return writerFailed || closingWriter.checkError();
    }

    private boolean finalizeExportFile() {
        if (exportFileTarget == null) {
            return false;
        }
        if (isStopped() || errorOccurred) {
            exportFileTarget.discard();
            return false;
        }
        try {
            exportFileTarget.publish();
            writeFile = exportFileTarget.getTargetFile();
            outputPublished = true;
            if (call == null) {
                exportFileTarget.release();
            }
            return false;
        } catch (Exception e) {
            exportFileTarget.discard();
            error("Unable to publish export file: " + e.getMessage());
            return true;
        }
    }

    public void write(String message) {
        checkCancelled();
        if (writer != null) {
            writer.write(message + "\n");
        }
    }

    private void appendInfo(String message) {
        synchronized (messageLock) {
            info.append(message).append("\n");
        }
    }

    private void createWriter() {
        if (writeFile != null) {
            this.writer = FileUtil.getPrintWriter(writeFile, "UTF-8", false);
        }
    }

    private void asyncCallBack(Context context) {
        if (call != null && context != null) {
            new Thread(() -> {
                try {
                    ContextUtils.setContext(context);
                    int n = 1;
                    while (!finish) {
                        try {
                            callUpdate();
                        } catch (RuntimeException e) {
                            log.warn("AsyncContext polling callback failed; it will be retried", e);
                        }
                        if (finish) {
                            break;
                        }
                        try {
                            Thread.sleep(2000L * n);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (n < 5) {
                            n++;
                        }
                    }
                } finally {
                    ContextUtils.removeContext();
                }
            }).start();
        }
    }

    private void publishTerminalUpdate() {
        RuntimeException lastFailure = null;
        boolean terminalUpdateSucceeded = false;
        try {
            for (int attempt = 1; attempt <= TERMINAL_UPDATE_MAX_ATTEMPTS; attempt++) {
                try {
                    callUpdate();
                    terminalUpdateSucceeded = true;
                    return;
                } catch (RuntimeException e) {
                    lastFailure = e;
                    log.warn("AsyncContext terminal callback failed, attempt {}/{}",
                            attempt, TERMINAL_UPDATE_MAX_ATTEMPTS, e);
                }
            }
            log.error("AsyncContext terminal callback failed after {} attempts",
                    TERMINAL_UPDATE_MAX_ATTEMPTS, lastFailure);
        } finally {
            if (!terminalUpdateSucceeded && exportFileTarget != null) {
                // The output may already be published, but staging is private
                // and may always be discarded after callback retries exhaust.
                exportFileTarget.release();
            }
        }
    }

    private void callUpdate() {
        if (call == null) {
            return;
        }
        synchronized (callbackLock) {
            while (true) {
                State updateState;
                Integer updateProgress;
                String infoMessage;
                String errorMessage;
                synchronized (this) {
                    updateState = state;
                    if (updateState != State.RUNNING && terminalUpdatePublished) {
                        return;
                    }
                    updateProgress = progress;
                    synchronized (messageLock) {
                        infoMessage = info.toString();
                        errorMessage = error.toString();
                        info = new StringBuffer();
                        error = new StringBuffer();
                    }
                }

                Map<String, Object> map = new HashMap<>();
                map.put("progress", updateProgress);
                if (!infoMessage.isEmpty()) {
                    map.put("info", infoMessage);
                }
                if (!errorMessage.isEmpty()) {
                    map.put("error", errorMessage);
                }
                map.put("status", updateState.name());
                if (updateState == State.FINISHED && updateProgress == 100 && outputPublished
                        && getOutputFile() != null) {
                    map.put("downloadUrl", getOutputFile().getAbsolutePath());
                } else if (updateState == State.STOP || updateState == State.ERROR) {
                    map.put("downloadUrl", "");
                }
                try {
                    call.update(map);
                } catch (RuntimeException e) {
                    restoreMessages(infoMessage, errorMessage);
                    throw e;
                }

                synchronized (this) {
                    if (state == updateState) {
                        if (updateState != State.RUNNING) {
                            terminalUpdatePublished = true;
                            if (updateState == State.FINISHED && exportFileTarget != null && outputPublished) {
                                exportFileTarget.release();
                            }
                        }
                        return;
                    }
                }
            }
        }
    }

    private void restoreMessages(String infoMessage, String errorMessage) {
        synchronized (messageLock) {
            if (!infoMessage.isEmpty()) {
                info.insert(0, infoMessage);
            }
            if (!errorMessage.isEmpty()) {
                error.insert(0, errorMessage);
            }
        }
    }

    @Override
    public void onStatementCreated(Statement statement) {
        currentStatement.set(statement);
        if (isStopped()) {
            cancelStatement(statement);
        }
    }

    @Override
    public void onStatementClosed(Statement statement) {
        currentStatement.compareAndSet(statement, null);
    }

    private void cancelStatement(Statement statement) {
        if (statement == null) {
            return;
        }
        try {
            statement.cancel();
        } catch (Exception e) {
            log.warn("Failed to cancel task JDBC statement", e);
        }
    }
}
