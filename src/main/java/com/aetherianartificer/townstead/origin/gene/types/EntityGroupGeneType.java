package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * Makes the entity count as a creature group (undead, arthropod, illager, aquatic)
 * for combat interactions: Smite/Bane bonus damage, harming-potion healing,
 * undead-targeting mobs. On 1.20.1 this is applied via a {@code getMobType} override
 * (vanilla then does the rest for free); on 1.21.1 the same effects are layered in
 * through the damage/effect hooks, since per-entity mob type was removed there.
 *
 * <p>JSON: {@code { "type":"pheno:entity_group", "group":"undead" }}</p>
 */
public final class EntityGroupGeneType implements GeneType {

    public static final String KEY = "pheno:entity_group";

    public enum Group {
        DEFAULT, UNDEAD, ARTHROPOD, ILLAGER, AQUATIC;

        public static Group byKey(String raw) {
            if (raw == null) return DEFAULT;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "undead" -> UNDEAD;
                case "arthropod" -> ARTHROPOD;
                case "illager" -> ILLAGER;
                case "aquatic", "water" -> AQUATIC;
                default -> DEFAULT;
            };
        }
    }

    public record Instance(Group group) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Group group = Group.byKey(GsonHelper.getAsString(json, "group", ""));
        if (group == Group.DEFAULT) return null;
        return new Instance(group);
    }
}
