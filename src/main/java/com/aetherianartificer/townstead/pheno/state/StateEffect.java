package com.aetherianartificer.townstead.pheno.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An independently contributed set of Pheno hooks for an open state. {@code tier} matches one
 * tier exactly; {@code minTier} matches that tier and every tier above it. {@code genes} overlays
 * the carrier's expressed genes while the effect matches, without touching the genotype.
 */
public record StateEffect(ResourceLocation id, ResourceLocation state, @Nullable String tier,
                          @Nullable String minTier, @Nullable Condition condition,
                          int priority, @Nullable Action onEnter, @Nullable Action onTierChange,
                          @Nullable Periodic whileActive, @Nullable Action onExit,
                          @Nullable Genes genes) {
    public static final String SCHEMA = "pheno:state_effect/v1";
    public record Periodic(int interval, double chance, Action action) {}
    public record Grant(ResourceLocation gene, @Nullable String variant) {}
    public record Genes(List<Grant> grant, List<ResourceLocation> suppress) {
        public Genes {
            grant = List.copyOf(grant);
            suppress = List.copyOf(suppress);
        }
    }

    /** Whether the effect's tier gate admits a resolved tier of {@code definition}. */
    public boolean admitsTier(EntityStateDefinition definition, @Nullable String currentTier, int currentIndex) {
        if (tier != null) return tier.equals(currentTier);
        if (minTier == null) return true;
        EntityStateDefinition.Tier floor = definition.tier(minTier);
        return floor != null && currentIndex >= definition.tiers().indexOf(floor);
    }

    static StateEffect parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        ResourceLocation state = DataPackLang.parseId(GsonHelper.getAsString(json, "state", ""));
        if (state == null) throw new IllegalArgumentException("'state' must be a resource id");
        String tier = json.has("tier") ? GsonHelper.getAsString(json, "tier", "").trim() : null;
        if (tier != null && tier.isEmpty()) throw new IllegalArgumentException("'tier' cannot be empty");
        String minTier = json.has("min_tier") ? GsonHelper.getAsString(json, "min_tier", "").trim() : null;
        if (minTier != null && minTier.isEmpty()) throw new IllegalArgumentException("'min_tier' cannot be empty");
        if (tier != null && minTier != null) throw new IllegalArgumentException("use 'tier' or 'min_tier', not both");
        Condition condition = null;
        if (json.has("condition")) {
            condition = Conditions.parse(json.get("condition"));
            if (condition == null) throw new IllegalArgumentException("'condition' is not a valid Pheno condition");
        }
        Genes genes = json.has("genes") ? genes(json.get("genes")) : null;
        Action enter = action(json, "on_enter");
        Action change = action(json, "on_tier_change");
        Action exit = action(json, "on_exit");
        Periodic periodic = null;
        if (json.has("while_active")) {
            if (!json.get("while_active").isJsonObject()) throw new IllegalArgumentException("'while_active' must be an object");
            JsonObject loop = json.getAsJsonObject("while_active");
            int interval = GsonHelper.getAsInt(loop, "interval", 20);
            double chance = GsonHelper.getAsDouble(loop, "chance", 1);
            Action action = Actions.parse(loop.get("do"));
            if (interval < 1) throw new IllegalArgumentException("while_active interval must be positive");
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("while_active chance must be in [0,1]");
            if (action == null) throw new IllegalArgumentException("while_active requires a valid 'do' action");
            periodic = new Periodic(interval, chance, action);
        }
        if (enter == null && change == null && exit == null && periodic == null && genes == null) {
            throw new IllegalArgumentException("state effect has no hooks");
        }
        return new StateEffect(id, state, tier, minTier, condition, GsonHelper.getAsInt(json, "priority", 0),
                enter, change, periodic, exit, genes);
    }

    private static Genes genes(JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("'genes' must be an object");
        JsonObject json = element.getAsJsonObject();
        List<Grant> grants = new ArrayList<>();
        for (JsonElement entry : array(json, "grant")) {
            if (entry.isJsonPrimitive()) {
                grants.add(new Grant(geneId(entry.getAsString()), null));
                continue;
            }
            if (!entry.isJsonObject()) throw new IllegalArgumentException("each grant must be a gene id or an object");
            JsonObject grant = entry.getAsJsonObject();
            String variant = grant.has("variant") ? GsonHelper.getAsString(grant, "variant").trim() : null;
            if (variant != null && variant.isEmpty()) throw new IllegalArgumentException("grant 'variant' cannot be empty");
            grants.add(new Grant(geneId(GsonHelper.getAsString(grant, "gene", "")), variant));
        }
        List<ResourceLocation> suppress = new ArrayList<>();
        for (JsonElement entry : array(json, "suppress")) {
            if (!entry.isJsonPrimitive()) throw new IllegalArgumentException("each suppress entry must be a gene id");
            suppress.add(geneId(entry.getAsString()));
        }
        if (grants.isEmpty() && suppress.isEmpty()) throw new IllegalArgumentException("'genes' grants and suppresses nothing");
        return new Genes(grants, suppress);
    }

    private static List<JsonElement> array(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException("'" + key + "' must be an array");
        List<JsonElement> out = new ArrayList<>();
        json.getAsJsonArray(key).forEach(out::add);
        return out;
    }

    private static ResourceLocation geneId(String raw) {
        ResourceLocation id = DataPackLang.parseId(raw);
        if (id == null) throw new IllegalArgumentException("'" + raw + "' is not a gene id");
        return id;
    }

    private static @Nullable Action action(JsonObject json, String key) {
        if (!json.has(key)) return null;
        JsonElement element = json.get(key);
        Action action = Actions.parse(element);
        if (action == null) throw new IllegalArgumentException("'" + key + "' is not a valid Pheno action");
        return action;
    }
}
