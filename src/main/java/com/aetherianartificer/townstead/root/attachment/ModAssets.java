package com.aetherianartificer.townstead.root.attachment;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a file from an installed mod's own {@code assets} folder. A server has no resource manager for
 * assets, but the mods' jars are right there, so a rig that points at another mod's model works on both sides.
 */
public final class ModAssets {

    private ModAssets() {}

    /** The bytes of {@code namespace:path}, from the mod of that name or any other that ships it. Null when none does. */
    @Nullable
    public static byte[] read(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0) return null;
        String namespace = id.substring(0, colon);
        String[] path = ("assets/" + namespace + "/" + id.substring(colon + 1)).split("/");
        try {
            //? if neoforge {
            net.neoforged.fml.ModList mods = net.neoforged.fml.ModList.get();
            //?} else {
            /*net.minecraftforge.fml.ModList mods = net.minecraftforge.fml.ModList.get();
            *///?}
            if (mods == null) return null;
            var named = mods.getModFileById(namespace);
            if (named != null) {
                byte[] bytes = bytesOf(named.getFile().findResource(path));
                if (bytes != null) return bytes;
            }
            for (var file : mods.getModFiles()) {
                byte[] bytes = bytesOf(file.getFile().findResource(path));
                if (bytes != null) return bytes;
            }
        } catch (Exception ignored) {
            // A jar that can't be read is a mod that doesn't have it.
        }
        return null;
    }

    @Nullable
    private static byte[] bytesOf(Path file) throws java.io.IOException {
        return file != null && Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }
}
