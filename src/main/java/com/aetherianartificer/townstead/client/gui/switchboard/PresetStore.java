package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.switchboard.SwitchboardPreset;
import com.aetherianartificer.townstead.switchboard.Systems;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Preset files: the player's own in {@code .minecraft/townstead/presets}, and any a modpack ships in
 * {@code config/townstead/presets}. Townstead's defaults are always first.
 */
final class PresetStore {
    private PresetStore() {}

    enum Source { BUILT_IN, MODPACK, SAVED }

    record Listed(SwitchboardPreset preset, Source source) {}

    static Path savedDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(Townstead.MOD_ID).resolve("presets");
    }

    private static Path modpackDirectory() {
        //? if >=1.21 {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(Townstead.MOD_ID).resolve("presets");
        //?} else {
        /*return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(Townstead.MOD_ID).resolve("presets");
        *///?}
    }

    static List<Listed> all(String defaultsName, String defaultsDescription) {
        List<Listed> out = new ArrayList<>();
        out.add(new Listed(new SwitchboardPreset(defaultsName, defaultsDescription, Map.of()), Source.BUILT_IN));
        out.add(new Listed(new SwitchboardPreset(
                Component.translatable("townstead.switchboard.presets.off").getString(),
                Component.translatable("townstead.switchboard.presets.off.description").getString(),
                Systems.everythingOff()), Source.BUILT_IN));
        read(modpackDirectory(), Source.MODPACK, out);
        read(savedDirectory(), Source.SAVED, out);
        return out;
    }

    private static void read(Path dir, Source source, List<Listed> out) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .forEach(p -> {
                        String fallback = p.getFileName().toString().replaceFirst("(?i)\\.json$", "");
                        try {
                            String text = Files.readString(p, StandardCharsets.UTF_8);
                            out.add(new Listed(SwitchboardPreset.fromJson(
                                    JsonParser.parseString(text).getAsJsonObject(), fallback), source));
                        } catch (Exception e) {
                            Townstead.LOGGER.warn("[Switchboard] Skipping unreadable preset {}", p, e);
                        }
                    });
        } catch (IOException e) {
            Townstead.LOGGER.warn("[Switchboard] Could not list presets in {}", dir, e);
        }
    }

    static Path save(SwitchboardPreset preset) throws IOException {
        Path dir = savedDirectory();
        Files.createDirectories(dir);
        String base = preset.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "preset";
        Path file = dir.resolve(base + ".json");
        for (int i = 2; Files.exists(file); i++) file = dir.resolve(base + "_" + i + ".json");
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(preset.toJson()),
                StandardCharsets.UTF_8);
        return file;
    }
}
