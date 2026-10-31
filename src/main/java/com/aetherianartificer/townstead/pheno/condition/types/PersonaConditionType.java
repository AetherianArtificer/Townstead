package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.persona.PersonaDefinition;
import com.aetherianartificer.townstead.persona.PersonaService;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * True when the villager in focus is a Persona, or with {@code persona} that one Persona. Lets
 * something that picks villagers at random leave the story characters alone.
 * <pre>{ "type": "pheno:persona", "inverted": true }</pre>
 */
public final class PersonaConditionType implements ConditionType {
    public static final String KEY = "pheno:persona";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String id = GsonHelper.getAsString(json, "persona", "");
        ResourceLocation wanted = id.isBlank() ? null : ResourceLocation.tryParse(id);
        return ctx -> {
            if (!(ctx.entity() instanceof VillagerEntityMCA villager)) return false;
            PersonaDefinition persona = PersonaService.definition(villager);
            return persona != null && (wanted == null || wanted.equals(persona.id()));
        };
    }
}
