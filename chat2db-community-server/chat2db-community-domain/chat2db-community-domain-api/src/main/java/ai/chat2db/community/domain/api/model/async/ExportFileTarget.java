package ai.chat2db.community.domain.api.model.async;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Holds the staging and final locations for an export file.
 *
 * <p>An export writes only to {@link #getStagingFile()}. Once its async task
     * completes without reporting an error, {@link #publish()} publishes that file at
 * {@link #getTargetFile()}. This keeps an existing target intact while an
 * overwrite export is still running.</p>
 */
public final class ExportFileTarget {

    private volatile Path targetFile;

    private final Path stagingFile;

    private final Path stagingDirectory;

    private final boolean renameIfTargetExists;

    private boolean published;

    public ExportFileTarget(File targetFile, File stagingFile, File stagingDirectory,
            boolean renameIfTargetExists) {
        this.targetFile = targetFile.toPath();
        this.stagingFile = stagingFile.toPath();
        this.stagingDirectory = stagingDirectory.toPath();
        this.renameIfTargetExists = renameIfTargetExists;
    }

    public File getTargetFile() {
        return targetFile.toFile();
    }

    public File getStagingFile() {
        return stagingFile.toFile();
    }

    /**
     * Publishes the staged export. The staging directory is deliberately
     * created beneath the target directory so publication can stay on the same
     * filesystem.
     */
    public synchronized void publish() throws IOException {
        if (published) {
            return;
        }
        if (renameIfTargetExists) {
            targetFile = publishWithoutReplacing();
        } else {
            replaceExistingTarget();
        }
        published = true;
    }

    /**
     * Releases private staging data after a terminal task update is persisted
     * or exhausts its retry budget. Publishing an output file is irreversible:
     * cleanup never changes a user-visible target.
     */
    public synchronized void release() {
        deleteStagingDirectory();
    }

    /**
     * Removes an incomplete staged export. It intentionally never changes a
     * final path because no final path is reserved before publication.
     */
    public synchronized void discard() {
        deleteStagingDirectory();
    }

    private void replaceExistingTarget() throws IOException {
        try {
            Files.move(stagingFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("Atomic replacement is required to publish an overwrite export", e);
        }
    }

    /**
     * Makes a completed staged file visible without ever pre-creating,
     * deleting, or replacing a candidate final path. A hard link is an atomic
     * same-filesystem create operation: if another process wins a name first,
     * this task simply tries the next one. Once the final link exists, removing
     * the private staging link cannot affect the published file.
     */
    private Path publishWithoutReplacing() throws IOException {
        Path requestedTarget = targetFile;
        String fileName = requestedTarget.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        String extension = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";

        for (int index = 0; index < 1000; index++) {
            Path candidate = index == 0 ? requestedTarget
                    : requestedTarget.resolveSibling(baseName + "_" + index + extension);
            if (linkStagingFile(candidate)) {
                return candidate;
            }
        }
        while (true) {
            Path candidate = requestedTarget.resolveSibling(baseName + "_" + UUID.randomUUID() + extension);
            if (linkStagingFile(candidate)) {
                return candidate;
            }
        }
    }

    private boolean linkStagingFile(Path candidate) throws IOException {
        try {
            Files.createLink(candidate, stagingFile);
        } catch (FileAlreadyExistsException ignored) {
            return false;
        } catch (UnsupportedOperationException e) {
            throw new IOException("Rename export requires filesystem support for atomic hard links", e);
        } catch (IOException e) {
            throw new IOException("Unable to safely publish renamed export; the export filesystem must support "
                    + "atomic hard links", e);
        }
        try {
            Files.deleteIfExists(stagingFile);
        } catch (IOException ignored) {
            // The final link is already published. release() will retry the
            // private staging cleanup without touching the final file.
        }
        return true;
    }

    private void deleteStagingDirectory() {
        if (Files.notExists(stagingDirectory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(stagingDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup; the completed file has already been published.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup; callers still retain the original error/result.
        }
    }
}
