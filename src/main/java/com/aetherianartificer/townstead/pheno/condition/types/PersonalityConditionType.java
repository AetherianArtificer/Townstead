package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.root.personality.PersonalityResolver;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashSet;
import java.util.Set;

/** Matches a villager's exact data-pack personality, MCA base personality, or either. */
public final class PersonalityConditionType implements ConditionType {
    public static final String KEY = "pheno:personality";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Set<String> wanted = new LinkedHashSet<>();
        JsonElement value = json.get("personality");
        if (value == null) return null;
        if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) wanted.add(normalize(entry.getAsString()));
            }
        } else if (value.isJsonPrimitive()) {
            wanted.add(normalize(value.getAsString()));
        }
        if (wanted.isEmpty()) return null;
        String match = GsonHelper.getAsString(json, "match", "either").toLowerCase(java.util.Locale.ROOT);
        if (!match.equals("exact") && !match.equals("base") && !match.equals("either")) return null;
        return ctx -> {
            if (!(ctx.entity() instanceof VillagerEntityMCA villager)) return false;
            String exact = normalize(TownsteadVillagers.get(villager).life().personalityId());
            String base = normalize(McaPersonalityCompat.id(
                    villager.getVillagerBrain().getPersonality()));
            if (base.equals("mca:unassigned")) {
                var resolved = PersonalityResolver.baseOf(exact);
                if (resolved != null) base = normalize(McaPersonalityCompat.id(resolved));
            }
            return switch (match) {
                case "exact" -> !exact.isEmpty() && wanted.contains(exact);
                case "base" -> wanted.contains(base);
                default -> (!exact.isEmpty() && wanted.contains(exact)) || wanted.contains(base);
            };
        };
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.indexOf(':') < 0 ? "mca:" + normalized : normalized;
    }
}
