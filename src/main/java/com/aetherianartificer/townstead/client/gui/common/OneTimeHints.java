package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Nudges that are shown once and then never again.
 *
 * <p>A tutorial hint held in a static field fires once per GAME SESSION, which means a player who
 * learned the thing on Monday is taught it again on Tuesday. "Once" has to outlive the process to
 * mean anything.</p>
 *
 * <p>This deliberately does not live in the config. A config file is a set of knobs a player is
 * invited to turn, and "have you seen the tooltip yet" is not one of those; putting it there would
 * add a line of noise to every Townstead config for every hint the UI ever grows. It is a scratch
 * file, and losing it costs one repeated tooltip.</p>
 */
public final class OneTimeHints {

    private OneTimeHints() {}

    public static final String CAREER_SWITCH = "career.switch";

    private static final String FILE = "townstead-hints.properties";
    private static Properties loaded;

    public static boolean seen(String key) {
        return Boolean.parseBoolean(properties().getProperty(key, "false"));
    }

    /** Records the hint as shown. Failure to write is not worth telling the player about. */
    public static void markSeen(String key) {
        Properties properties = properties();
        if (Boolean.parseBoolean(properties.getProperty(key, "false"))) return;
        properties.setProperty(key, "true");
        Path path = path();
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "Townstead UI hints already shown. Delete to see them again.");
            }
        } catch (IOException | RuntimeException ignored) {
            // In-memory only for the rest of the session.
        }
    }

    private static synchronized Properties properties() {
        if (loaded != null) return loaded;
        loaded = new Properties();
        Path path = path();
        if (path != null && Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                loaded.load(in);
            } catch (IOException | RuntimeException ignored) {
                // Unreadable is the same as unseen.
            }
        }
        return loaded;
    }

    /** Resolved from the game directory rather than from the loader's config path, which differs. */
    private static Path path() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) return null;
        return minecraft.gameDirectory.toPath().resolve("config").resolve(FILE);
    }
}
