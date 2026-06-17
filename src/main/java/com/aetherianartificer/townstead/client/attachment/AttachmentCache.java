package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Content-addressed on-disk cache for attachment blobs at
 * {@code <gameDir>/townstead/attachment-cache/<sha1>}. Pulled blobs are written here
 * and reused across sessions; a blob is only re-pulled when its hash changes on the
 * server. Pruned oldest-first when total size exceeds {@value #CAP_BYTES} bytes.
 */
public final class AttachmentCache {

    private static final long CAP_BYTES = 1024L * 1024L * 1024L;

    private AttachmentCache() {}

    private static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("townstead").resolve("attachment-cache");
    }

    public static boolean has(String sha1) {
        return Files.isRegularFile(dir().resolve(sha1));
    }

    public static byte[] read(String sha1) {
        try {
            Path file = dir().resolve(sha1);
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static void write(String sha1, byte[] bytes) {
        try {
            Path dir = dir();
            Files.createDirectories(dir);
            Files.write(dir.resolve(sha1), bytes);
            prune(dir);
        } catch (IOException e) {
            Townstead.LOGGER.warn("Failed to cache attachment blob {}", sha1, e);
        }
    }

    private static void prune(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            long total = 0;
            for (Path f : files) total += Files.size(f);
            if (total <= CAP_BYTES) return;
            List<Path> oldestFirst = files.stream()
                    .sorted(Comparator.comparingLong(p -> p.toFile().lastModified())).toList();
            for (Path f : oldestFirst) {
                if (total <= CAP_BYTES) break;
                long size = Files.size(f);
                if (Files.deleteIfExists(f)) total -= size;
            }
        }
    }
}
