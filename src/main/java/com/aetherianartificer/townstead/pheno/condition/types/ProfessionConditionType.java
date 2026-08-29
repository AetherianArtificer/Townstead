package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.HashSet;
import java.util.Set;

/**
 * {@code pheno:profession} — the entity's villager profession, by id
 * ({@code profession}: one id or a list, e.g. {@code "townstead:cook"}).
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
        if (json.has("profession")) {
            if (json.get("profession").isJsonArray()) {
                for (var element : json.getAsJsonArray("profession")) {
                    professions.add(element.getAsString());
                }
            } else {
                professions.add(GsonHelper.getAsString(json, "profession"));
            }
        }
        return Condition.subjectAware(ctx -> test(ctx, professions));
    }

    private static boolean test(ConditionContext ctx, Set<String> professions) {
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
            ResourceLocation expected = ResourceLocation.tryParse(authored);
            if (expected != null && com.aetherianartificer.townstead.profession.ProfessionIdentity
                    .matches(ctx.entity(), actual, expected)) return true;
        }
        return false;
    }
}
