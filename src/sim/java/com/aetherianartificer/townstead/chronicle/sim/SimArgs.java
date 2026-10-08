package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed command line for the harness. Every option has a working default. */
public final class SimArgs {

    private final Map<String, String> options = new LinkedHashMap<>();
    public final String mode;

    public SimArgs(String[] args) {
        String parsedMode = "village";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                if (i == 0) parsedMode = arg;
                continue;
            }
            String name = arg.substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                options.put(name, args[++i]);
            } else {
                options.put(name, "");
            }
        }
        this.mode = parsedMode;
    }

    public boolean has(String name) {
        return options.containsKey(name);
    }

    public String text(String name, String fallback) {
        String value = options.get(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public int number(String name, int fallback) {
        return (int) number(name, (long) fallback);
    }

    public long number(String name, long fallback) {
        String value = options.get(name);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public float decimal(String name, float fallback) {
        String value = options.get(name);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public Path path(String name, String fallback) {
        return Path.of(text(name, fallback));
    }

    public ResourceLocation id(String name, String fallback) {
        return DataPackLang.parseId(text(name, fallback));
    }

    /** {@code --items "tag=id,id;tag=id"}: what a pack would put in a tag. */
    public Map<ResourceLocation, List<ResourceLocation>> itemTags() {
        Map<ResourceLocation, List<ResourceLocation>> tags = new LinkedHashMap<>();
        String spec = text("items", "");
        if (spec.isEmpty()) return tags;
        for (String entry : spec.split(";")) {
            int split = entry.indexOf('=');
            if (split <= 0) continue;
            ResourceLocation tag = DataPackLang.parseId(entry.substring(0, split).trim());
            if (tag == null) continue;
            tags.put(tag, SampleItems.ids(entry.substring(split + 1).split(",")));
        }
        return tags;
    }
}
