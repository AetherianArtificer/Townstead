package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.HashSet;
import java.util.Set;

/**
 * {@code pheno:profession} — the entity's villager profession, by id
 * ({@code profession}: one id, tag, or a list, e.g. {@code "townstead:cook"} or
 * {@code "#townstead_hangouts:on_duty"}). Optional {@code path} accepts one Career Path id or a
 * list and is checked after canonical Career/compatibility-alias resolution.
 * Non-villagers never match. The profession is entity-synced villager data,
 * so this evaluates identically on server and client.
 */
public final class ProfessionConditionType implements ConditionType {

    public static final String KEY = "pheno:profession";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Set<String> professions = new HashSet<>();
        Set<String> paths = new HashSet<>();
        if (json.has("profession")) {
            if (json.get("profession").isJsonArray()) {
                for (var element : json.getAsJsonArray("profession")) {
                    professions.add(element.getAsString());
                }
            } else {
                professions.add(GsonHelper.getAsString(json, "profession"));
            }
        }
        if (json.has("path")) {
            if (json.get("path").isJsonArray()) {
                for (var element : json.getAsJsonArray("path")) paths.add(element.getAsString());
            } else {
                paths.add(GsonHelper.getAsString(json, "path"));
            }
        }
        Condition parsed = ctx -> test(ctx, professions, paths);
        // Historical Pheno subjects carry a profession but not learned/implied Path state.
        return paths.isEmpty() ? Condition.subjectAware(parsed) : parsed;
    }

    private static boolean test(ConditionContext ctx, Set<String> professions, Set<String> paths) {
        ResourceLocation actual;
        if (ctx.subject() != null) {
            actual = ResourceLocation.tryParse(ctx.subject().professionId());
        } else {
            if (!(ctx.entity() instanceof VillagerDataHolder holder)) return false;
            VillagerProfession profession = holder.getVillagerData().getProfession();
            actual = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        }
        if (actual == null) return false;
        for (String authored : professions) {
            if (authored.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(authored.substring(1));
                if (tagId != null && inTag(actual, tagId) && matchesPath(ctx, actual, paths)) return true;
                continue;
            }
            ResourceLocation expected = ResourceLocation.tryParse(authored);
            if (expected != null && com.aetherianartificer.townstead.profession.ProfessionIdentity
                    .matches(ctx.entity(), actual, expected) && matchesPath(ctx, actual, paths)) return true;
        }
        return false;
    }

    private static boolean matchesPath(ConditionContext ctx, ResourceLocation actual, Set<String> paths) {
        if (paths.isEmpty()) return true;
        if (ctx.entity() == null) return false;
        ResourceLocation career = com.aetherianartificer.townstead.profession.def.ProfessionDefs.canonicalId(actual);
        var path = com.aetherianartificer.townstead.profession.ProfessionIdentity.path(ctx.entity(), career);
        return path != null && paths.contains(path.id());
    }

    private static boolean inTag(ResourceLocation profession, ResourceLocation tagId) {
        TagKey<VillagerProfession> tag = TagKey.create(Registries.VILLAGER_PROFESSION, tagId);
        return BuiltInRegistries.VILLAGER_PROFESSION.getTag(tag).map(entries -> entries.stream()
                .anyMatch(holder -> holder.unwrapKey().map(key -> key.location().equals(profession))
                        .orElse(false))).orElse(false);
    }
}
