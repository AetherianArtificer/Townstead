package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** One compact data-defined merchant offer, grouped by level through {@code trade/*.json}. */
public record TradeDef(
        ResourceLocation costItem, int costCount,
        @Nullable ResourceLocation secondaryCostItem, int secondaryCostCount,
        ResourceLocation resultItem, int resultCount,
        int maxUses, int villagerXp, float priceMultiplier,
        @Nullable ResourceLocation profession, @Nullable String path,
        Condition requirements) {

    @Nullable
    public static TradeDef parse(JsonObject json) {
        return parse(json, null, 1);
    }

    @Nullable
    public static TradeDef parse(JsonObject json, @Nullable ResourceLocation profession) {
        return parse(json, profession, 1);
    }

    @Nullable
    public static TradeDef parse(JsonObject json, @Nullable ResourceLocation profession,
                                 int merchantLevel) {
        // The compact contract is deliberately one shape. Old nested stacks and the bespoke
        // requires_skill gate are rejected rather than becoming a second authoring language.
        if (json.has("requires_skill") || object(json, "cost") || object(json, "result")
                || object(json, "secondary_cost")) return null;
        ResourceLocation cost = itemId(json, "cost");
        ResourceLocation result = itemId(json, "result");
        if (cost == null || result == null) return null;
        ResourceLocation secondary = itemId(json, "secondary_cost");

        String path = null;
        if (json.has("path")) {
            if (profession == null || !json.get("path").isJsonPrimitive()
                    || !json.getAsJsonPrimitive("path").isString()
                    || json.get("path").getAsString().isBlank()) return null;
            path = json.get("path").getAsString();
        }

        Condition requirements = Conditions.ALWAYS;
        if (json.has("requires")) {
            JsonElement authored = json.get("requires");
            if (authored.isJsonPrimitive() && authored.getAsJsonPrimitive().isString()) {
                ResourceLocation skill = skillRef(authored.getAsString(), profession, path);
                if (skill == null) return null;
                requirements = ctx -> com.aetherianartificer.townstead.profession.skill.LearnedSkills
                        .has(ctx.entity(), skill);
            } else if (authored.isJsonObject()) {
                JsonElement scoped = authored.deepCopy();
                scopeSkillRefs(scoped, profession, path);
                requirements = Conditions.parse(scoped);
                if (requirements == null) return null;
            } else {
                return null;
            }
        }

        int level = Math.max(1, merchantLevel);
        return new TradeDef(
                cost, count(json, "cost_count"),
                secondary, secondary == null ? 0 : count(json, "secondary_cost_count"),
                result, count(json, "result_count"),
                GsonHelper.getAsInt(json, "max_uses", defaultUses(level)),
                GsonHelper.getAsInt(json, "villager_xp", defaultVillagerXp(level)),
                GsonHelper.getAsFloat(json, "price_multiplier", 0.05f),
                profession, path,
                requirements);
    }

    public boolean eligible(LivingEntity entity) {
        if (entity == null || !onPath(entity)) return false;
        return requirements.test(new ConditionContext(entity));
    }

    private boolean onPath(LivingEntity entity) {
        if (path == null) return true;
        if (profession == null) return false;
        ProfessionPaths.Path active = com.aetherianartificer.townstead.profession
                .ProfessionIdentity.path(entity, profession);
        return active != null && path.equals(active.id());
    }

    private static int defaultUses(int merchantLevel) {
        return merchantLevel == 1 ? 16 : 12;
    }

    private static int defaultVillagerXp(int merchantLevel) {
        return switch (Math.min(5, merchantLevel)) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 15;
            default -> 20;
        };
    }

    private static boolean object(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonObject();
    }

    private static @Nullable ResourceLocation itemId(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isString()) return null;
        String raw = json.get(key).getAsString();
        return raw.isBlank() ? null : ResourceLocation.tryParse(raw);
    }

    private static int count(JsonObject json, String key) {
        try {
            return Math.max(1, GsonHelper.getAsInt(json, key, 1));
        } catch (RuntimeException ignored) {
            return 1;
        }
    }

    static @Nullable ResourceLocation skillRef(
            String raw, @Nullable ResourceLocation profession, @Nullable String path) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.contains(":")) return ResourceLocation.tryParse(raw);
        if (profession == null) return null;
        return ResourceLocation.tryParse(
                profession.getNamespace() + ":" + profession.getPath() + "/"
                        + (path == null ? "" : path + "/") + raw);
    }

    /** Bare skill ids inside any nested pheno:skill condition share the owning profession. */
    private static void scopeSkillRefs(JsonElement element, @Nullable ResourceLocation profession,
                                       @Nullable String path) {
        if (element == null || profession == null) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                scopeSkillRefs(child, profession, path);
            }
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if ("pheno:skill".equals(GsonHelper.getAsString(object, "type", ""))
                && object.has("skill") && object.get("skill").isJsonPrimitive()) {
            ResourceLocation skill = skillRef(object.get("skill").getAsString(), profession, path);
            if (skill != null) object.addProperty("skill", skill.toString());
        }
        for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
            scopeSkillRefs(entry.getValue(), profession, path);
        }
    }
}
