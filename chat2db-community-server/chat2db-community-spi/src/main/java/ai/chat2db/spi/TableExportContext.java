package ai.chat2db.spi;

import ai.chat2db.community.domain.api.model.task.ArtifactDraft;
import ai.chat2db.community.domain.api.service.task.TaskCancelable;
import ai.chat2db.community.domain.api.service.task.TaskExecutionContext;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.Map;

/** Stages one table on disk so a skipped table contributes no partial SQL. */
final class TableExportContext implements TaskExecutionContext, AutoCloseable {

    private final TaskExecutionContext delegate;
    private final Path temporaryFile;
    private final DataOutputStream output;
    private long writes;
    private UncheckedIOException writeFailure;

    TableExportContext(TaskExecutionContext delegate) throws IOException {
        this.delegate = delegate;
        temporaryFile = Files.createTempFile("chat2db-table-export-", ".tmp");
        try {
            output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporaryFile)));
        } catch (IOException failure) {
            Files.deleteIfExists(temporaryFile);
            throw failure;
        }
    }

    @Override
    public void write(String content) {
        delegate.checkCancelled();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try {
            // Preserve write boundaries: the task writer adds a newline to each write.
            output.writeInt(bytes.length);
            output.write(bytes);
            writes++;
        } catch (IOException failure) {
            writeFailure = new UncheckedIOException("Could not stage table export", failure);
            throw writeFailure;
        }
    }

    void rethrowWriteFailure() {
        if (writeFailure != null) {
            throw writeFailure;
        }
    }

    void publish() throws IOException {
        output.close();
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(temporaryFile)))) {
            for (long index = 0; index < writes; index++) {
                delegate.checkCancelled();
                byte[] bytes = new byte[input.readInt()];
                input.readFully(bytes);
                delegate.write(new String(bytes, StandardCharsets.UTF_8));
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            output.close();
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    @Override
    public void reportProgress(int progress, String stage, String message) {
        delegate.reportProgress(progress, stage, message);
    }

    @Override
    public void logInfo(String code, String message) {
        delegate.logInfo(code, message);
    }

    @Override
    public void logInfo(String code, String message, Map<String, Object> details) {
        delegate.logInfo(code, message, details);
    }

    @Override
    public void logWarn(String code, String message, Map<String, Object> details) {
        delegate.logWarn(code, message, details);
    }

    @Override
    public void logError(String code, String message, Map<String, Object> details) {
        delegate.logError(code, message, details);
    }

    @Override
    public void checkCancelled() {
        delegate.checkCancelled();
    }

    @Override
    public void registerCancelable(TaskCancelable resource) {
        delegate.registerCancelable(resource);
    }

    @Override
    public ArtifactDraft createArtifact(String outputDirectory, String fileName, String mediaType) {
        return delegate.createArtifact(outputDirectory, fileName, mediaType);
    }

    @Override
    public void onStatementCreated(Statement statement) {
        delegate.onStatementCreated(statement);
    }

    @Override
    public void onStatementClosed(Statement statement) {
        delegate.onStatementClosed(statement);
    }
}
