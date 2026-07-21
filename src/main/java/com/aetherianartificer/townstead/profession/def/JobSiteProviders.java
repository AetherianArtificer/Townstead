package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Registry of {@link JobSiteProvider} parsers keyed by a {@code poi} entry's {@code "type"}.
 * Built-ins self-register so parsing works anywhere (including loaders and tests); mods and
 * future provider kinds (conditional sites, multiblocks) add theirs via {@link #register}.
 */
public final class JobSiteProviders {

    public interface Parser {
        @Nullable JobSiteProvider parse(JsonObject json);
    }

    private static final Map<String, Parser> PARSERS = new LinkedHashMap<>();

    static {
        register(JobSiteProvider.JobBlock.KEY, json -> {
            Set<ResourceLocation> blocks = new LinkedHashSet<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "blocks", new JsonArray())) {
                ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
                if (id != null) blocks.add(id);
            }
            if (json.has("block")) {
                ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "block", ""));
                if (id != null) blocks.add(id);
            }
            return blocks.isEmpty() ? null : new JobSiteProvider.JobBlock(blocks);
        });
        register(JobSiteProvider.Building.KEY, json -> {
            List<String> prefixes = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "type_prefixes", new JsonArray())) {
                if (e.isJsonPrimitive()) prefixes.add(e.getAsString());
            }
            if (json.has("type_prefix")) prefixes.add(GsonHelper.getAsString(json, "type_prefix", ""));
            prefixes.removeIf(String::isBlank);
            return prefixes.isEmpty() ? null : new JobSiteProvider.Building(prefixes);
        });
        register(JobSiteProvider.Always.KEY, json -> new JobSiteProvider.Always());
    }

    private JobSiteProviders() {}

    public static void register(String key, Parser parser) {
        if (key == null || parser == null) return;
        PARSERS.put(key.toLowerCase(Locale.ROOT), parser);
    }

    public static boolean knows(String key) {
        return key != null && PARSERS.containsKey(key.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public static JobSiteProvider parse(JsonObject json) {
        Parser parser = PARSERS.get(GsonHelper.getAsString(json, "type", "").toLowerCase(Locale.ROOT));
        return parser == null ? null : parser.parse(json);
    }
}
