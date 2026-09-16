package com.aetherianartificer.townstead.clothing.policy;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Every wardrobe policy the pack declares, from {@code data/<ns>/wardrobe_policy/*.json}. */
public final class WardrobePolicies {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/WardrobePolicies");

    private static volatile List<WardrobePolicy> POLICIES = List.of();

    private WardrobePolicies() {}

    public static void replaceAll(List<WardrobePolicy> policies) {
        POLICIES = List.copyOf(policies);
    }

    public static List<WardrobePolicy> all() {
        return POLICIES;
    }

    public static @org.jetbrains.annotations.Nullable WardrobePolicy byId(ResourceLocation id) {
        if (id == null) return null;
        for (WardrobePolicy policy : POLICIES) {
            if (id.equals(policy.id())) return policy;
        }
        return null;
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {

        public Loader() {
            super(new Gson(), "wardrobe_policy");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            List<WardrobePolicy> policies = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : entries.entrySet()) {
                ResourceLocation id = e.getKey();
                try {
                    JsonObject obj = GsonHelper.convertToJsonObject(e.getValue(), id.toString());
                    TownsteadSchema.validate(obj, WardrobePolicy.SCHEMA);
                    if (obj.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(obj.get("mods")))) {
                        LOGGER.debug("Wardrobe policy {} skipped: mods gate unmet or malformed", id);
                        continue;
                    }
                    WardrobePolicy policy = WardrobePolicy.parse(id, obj);
                    if (policy == null) {
                        LOGGER.warn("Wardrobe policy {} rejected: unknown scope, a culture scope with no cultures,"
                                + " an armour rule, or a layer rule that names nothing", id);
                        continue;
                    }
                    policies.add(policy);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Wardrobe policy {} rejected: {}", id, ex.getMessage());
                }
            }
            replaceAll(policies);
            if (!policies.isEmpty()) LOGGER.info("Loaded {} wardrobe policies", policies.size());
        }
    }
}
