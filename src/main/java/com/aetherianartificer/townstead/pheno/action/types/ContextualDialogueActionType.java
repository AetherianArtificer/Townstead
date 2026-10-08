package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.dialogue.contextual.DialogueDirector;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashSet;
import java.util.Set;

/** {@code pheno:contextual_dialogue}: request an intent; palettes decide the actual localized line. */
public final class ContextualDialogueActionType implements ActionType {
    public static final String KEY = "pheno:contextual_dialogue";
    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        String intent = GsonHelper.getAsString(json, "intent", "").trim();
        if (intent.isEmpty()) return null;
        Set<String> context = strings(json, "context");
        Set<String> relationships = strings(json, "relationship");
        return ctx -> {
            if (!(ctx.entity() instanceof VillagerEntityMCA villager)
                    || !DialogueDirector.speak(villager, intent, context, relationships, ctx.other())) {
                ctx.fail();
            }
        };
    }

    private static Set<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement value : GsonHelper.getAsJsonArray(json, key)) result.add(value.getAsString());
        return Set.copyOf(result);
    }
}
