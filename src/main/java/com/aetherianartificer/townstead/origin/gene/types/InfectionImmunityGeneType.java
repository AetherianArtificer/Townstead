package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;

/**
 * A race immune to zombie infection: a zombie bite never starts the infection and the per-tick
 * progression can't advance, so it never converts to a zombie villager. Decoupled from
 * {@code entity_group} on purpose — an undead horse-bodied race is still "undead" for combat but
 * shouldn't ride MCA's humanoid zombie-villager conversion (that pipeline would need a per-body
 * zombie model). Enforced by {@code VillagerInfectionImmunityMixin} gating MCA's
 * {@code setInfectionProgress} chokepoint.
 *
 * <p>JSON: {@code { "type":"townstead_origins:infection_immune" }}</p>
 */
public final class InfectionImmunityGeneType implements GeneType {

    public static final String KEY = "townstead_origins:infection_immune";

    public record Instance() implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        return new Instance();
    }
}
