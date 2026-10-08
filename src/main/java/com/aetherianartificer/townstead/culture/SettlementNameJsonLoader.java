package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.naming.NamePools;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.resources.WeightedPool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads {@code data/<namespace>/settlement_name/<path>.json}. */
public final class SettlementNameJsonLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/SettlementNames");
    private static final Gson GSON = new Gson();
    private static final String SCHEMA = "townstead:settlement_name/v1";
    private static final Pattern SLOT = Pattern.compile("\\{([a-z0-9_./-]+)}");
    private static final int VALUE_LIMIT = 250_000;

    private final boolean faction;
    public SettlementNameJsonLoader() { this(false); }
    public SettlementNameJsonLoader(boolean faction) {
        super(GSON, faction ? "faction_name" : "settlement_name");
        this.faction = faction;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resources,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, SettlementNamePool> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), id.toString());
                TownsteadSchema.validate(root, faction ? "townstead:faction_name/v1" : SCHEMA);
                Parsed parsed = parse(id, root);
                if (parsed.forms() == null || parsed.values().isEmpty()) {
                    LOGGER.warn("Settlement names {} hold none that are usable, skipping", id);
                    continue;
                }
                loaded.put(id, new SettlementNamePool(id, parsed.forms(), parsed.values()));
            } catch (Exception exception) {
                LOGGER.warn("Could not load settlement names {}", id, exception);
            }
        }
        if (faction) FactionNaming.replace(loaded); else SettlementNamePools.replace(loaded);
        LOGGER.info("Loaded {} settlement-name list(s)", loaded.size());
    }

    static Set<String> values(JsonElement element) {
        Set<String> values = new LinkedHashSet<>();
        if (element == null) return values;
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && !value.getAsString().isBlank()) values.add(value.getAsString().trim());
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> value : element.getAsJsonObject().entrySet()) {
                if (value.getKey().isBlank()) continue;
                try {
                    if (value.getValue().getAsInt() > 0) values.add(value.getKey().trim());
                } catch (RuntimeException ignored) {
                    // NamePools reports the malformed weight; it is not a member of the pool.
                }
            }
        }
        return values;
    }

    static Parsed parse(ResourceLocation id, JsonObject root) {
        WeightedPool.Mutable<SettlementNamePool.Form> forms = new WeightedPool.Mutable<>(
                new SettlementNamePool.Form("", Map.of()));
        Set<String> allValues = new LinkedHashSet<>();
        boolean any = false;

        Set<String> literals = values(root.get("names"));
        for (String literal : literals) {
            forms.add(new SettlementNamePool.Form(literal, Map.of()), 1.0F);
            allValues.add(literal);
            any = true;
        }

        JsonObject partJson = GsonHelper.getAsJsonObject(root, "parts", null);
        Map<String, WeightedPool<String>> parts = new LinkedHashMap<>();
        Map<String, List<String>> partValues = new LinkedHashMap<>();
        if (partJson != null) {
            for (Map.Entry<String, JsonElement> entry : partJson.entrySet()) {
                JsonObject wrapper = new JsonObject();
                wrapper.add("values", entry.getValue());
                WeightedPool<String> pool = NamePools.pool(wrapper, "values", id);
                List<String> values = List.copyOf(values(entry.getValue()));
                if (pool != null && !values.isEmpty()) {
                    parts.put(entry.getKey(), pool);
                    partValues.put(entry.getKey(), values);
                }
            }
        }

        JsonElement patterns = root.get("patterns");
        if (patterns != null && patterns.isJsonArray()) {
            for (JsonElement value : patterns.getAsJsonArray()) {
                if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    any |= addPattern(forms, allValues, value.getAsString(), 1, parts, partValues);
                }
            }
        } else if (patterns != null && patterns.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : patterns.getAsJsonObject().entrySet()) {
                int weight;
                try {
                    weight = entry.getValue().getAsInt();
                } catch (RuntimeException ignored) {
                    continue;
                }
                any |= addPattern(forms, allValues, entry.getKey(), weight, parts, partValues);
            }
        }
        return new Parsed(any ? forms : null, Set.copyOf(allValues));
    }

    private static boolean addPattern(WeightedPool.Mutable<SettlementNamePool.Form> forms,
                                      Set<String> allValues, String raw, int weight,
                                      Map<String, WeightedPool<String>> parts,
                                      Map<String, List<String>> partValues) {
        String template = raw == null ? "" : raw.trim();
        if (template.isEmpty() || weight <= 0) return false;
        Set<String> slots = slots(template);
        if (slots.isEmpty() || !parts.keySet().containsAll(slots)) return false;
        Map<String, WeightedPool<String>> used = new LinkedHashMap<>();
        for (String slot : slots) used.put(slot, parts.get(slot));
        forms.add(new SettlementNamePool.Form(template, used), (float) Math.sqrt(weight));
        expand(template, List.copyOf(slots), 0, new LinkedHashMap<>(), partValues, allValues);
        return true;
    }

    private static Set<String> slots(String template) {
        Set<String> slots = new LinkedHashSet<>();
        Matcher matcher = SLOT.matcher(template);
        while (matcher.find()) slots.add(matcher.group(1));
        return slots;
    }

    private static void expand(String template, List<String> slots, int index,
                               Map<String, String> chosen, Map<String, List<String>> parts,
                               Set<String> out) {
        if (out.size() >= VALUE_LIMIT) return;
        if (index >= slots.size()) {
            String value = template;
            for (Map.Entry<String, String> part : chosen.entrySet()) {
                value = value.replace("{" + part.getKey() + "}", part.getValue());
            }
            if (!value.isBlank()) out.add(value.trim());
            return;
        }
        String slot = slots.get(index);
        for (String value : parts.getOrDefault(slot, List.of())) {
            chosen.put(slot, value);
            expand(template, slots, index + 1, chosen, parts, out);
            if (out.size() >= VALUE_LIMIT) break;
        }
        chosen.remove(slot);
    }

    record Parsed(WeightedPool<SettlementNamePool.Form> forms, Set<String> values) {}
}
