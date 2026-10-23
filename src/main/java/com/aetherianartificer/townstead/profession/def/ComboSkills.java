package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and datapack loader for Combo Skills ({@code data/<ns>/combo_skill/*.json}),
 * replaced wholesale each reload. Unlocks are DERIVED, never stored: a character has a Combo
 * Skill whenever their career levels meet every threshold, so history is the only ledger —
 * the same principle as skill points. Cached briefly per entity because the capability layer
 * asks often.
 */
public final class ComboSkills {

    private static final long CACHE_TICKS = 100L;
    private static volatile Map<ResourceLocation, ComboSkillDef> ENTRIES = Map.of();
    private static final Map<UUID, CachedUnlocks> UNLOCK_CACHE = new ConcurrentHashMap<>();
    private record CachedUnlocks(List<ComboSkillDef> unlocked, long expiresAt) {}

    private ComboSkills() {}

    public static void replaceAll(Map<ResourceLocation, ComboSkillDef> next) {
        ENTRIES = Map.copyOf(next);
        UNLOCK_CACHE.clear();
    }

    public static Map<ResourceLocation, ComboSkillDef> all() {
        return ENTRIES;
    }

    /** The Combo Skills this character's career history has earned. */
    public static List<ComboSkillDef> unlockedFor(LivingEntity entity) {
        if (ENTRIES.isEmpty() || entity == null) return List.of();
        long now = entity.level().getGameTime();
        CachedUnlocks cached = UNLOCK_CACHE.get(entity.getUUID());
        if (cached != null && now < cached.expiresAt()) return cached.unlocked();
        List<ComboSkillDef> unlocked = computeUnlocked(entity);
        UNLOCK_CACHE.put(entity.getUUID(), new CachedUnlocks(unlocked, now + CACHE_TICKS));
        return unlocked;
    }

    /** Uncached check, for before/after diffs around an XP gain. */
    public static List<ComboSkillDef> computeUnlocked(LivingEntity entity) {
        var store = com.aetherianartificer.townstead.profession.career.CareerTreeRows.storeOf(entity);
        if (store == null) return List.of();
        List<ComboSkillDef> unlocked = new ArrayList<>();
        for (ComboSkillDef def : ENTRIES.values()) {
            boolean met = true;
            for (Map.Entry<ResourceLocation, Integer> threshold : def.thresholds().entrySet()) {
                if (com.aetherianartificer.townstead.villager.ProfessionProgress
                        .getTier(store, threshold.getKey()) < threshold.getValue()) {
                    met = false;
                    break;
                }
            }
            if (met) unlocked.add(def);
        }
        return List.copyOf(unlocked);
    }

    /** Drop a character's cached unlocks (career levels just changed). */
    public static void invalidate(LivingEntity entity) {
        if (entity != null) UNLOCK_CACHE.remove(entity.getUUID());
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {

        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> entry
                    : manager.listResources("combo_skill", path -> path.getPath().endsWith(".json")).entrySet()) {
                try (var reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                    ResourceLocation file = entry.getKey();
                    String path = file.getPath();
                    ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                            + path.substring("combo_skill/".length(), path.length() - ".json".length()));
                    if (id == null) continue;
                    out.put(id, JsonParser.parseReader(reader).getAsJsonObject());
                } catch (Exception error) {
                    Townstead.LOGGER.warn("Could not read combo skill {}: {}", entry.getKey(), error.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<String, String> lang = DataPackLang.loadLangIndex(manager);
            Diagnostics diagnostics = new Diagnostics();
            Map<ResourceLocation, ComboSkillDef> defs = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.entrySet()) {
                JsonObject obj = entry.getValue();
                if (obj.has("mods") && !Boolean.TRUE.equals(
                        com.aetherianartificer.townstead.data.ModGate.evaluate(obj.get("mods")))) {
                    continue;
                }
                diagnostics.forResource(entry.getKey());
                ComboSkillDef def = ComboSkillDef.parse(entry.getKey(), obj, lang, diagnostics);
                if (def != null) {
                    defs.put(entry.getKey(), def);
                } else {
                    Townstead.LOGGER.warn("Combo skill {} is malformed; skipped "
                            + "(needs a 'professions' object with 2+ career: level entries)", entry.getKey());
                }
            }
            replaceAll(defs);
            if (!defs.isEmpty()) {
                Townstead.LOGGER.info("Loaded {} combo skill(s)", defs.size());
            }
        }
    }
}
