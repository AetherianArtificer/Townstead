package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.ability.Ability;
import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.InsulationGeneType;
import com.aetherianartificer.townstead.root.gene.types.MetabolismGeneType;
import com.aetherianartificer.townstead.root.gene.types.ThermalToleranceGeneType;
import com.aetherianartificer.townstead.root.locomotion.SwimProfile;
import com.aetherianartificer.townstead.root.locomotion.SwimStyle;
import net.minecraft.world.entity.LivingEntity;

/**
 * Everything the root says about a body's relationship with heat, resolved once from the expressed
 * genes: where it rests, how wide its comfort is, how hard cold and heat pull on it, its innate
 * insulation, whether it makes its own warmth, and whether rain and swimming leave it soaked.
 * Diving and fish-style swimmers stay dry; cold water still chills them.
 */
public record ThermalProfile(float neutral, float band, float cold, float heat, float insulation, boolean sheds,
                             boolean ectotherm, boolean suppressed, boolean waterproof) {

    public static final ThermalProfile DEFAULT = new ThermalProfile(TemperatureData.DEFAULT_NEUTRAL,
            TemperatureData.DEFAULT_BAND, 1f, 1f, 0f, false, false, false);

    public ThermalProfile(float neutral, float band, float cold, float heat, float insulation, boolean sheds,
                          boolean ectotherm, boolean suppressed) {
        this(neutral, band, cold, heat, insulation, sheds, ectotherm, suppressed, false);
    }

    /** Whether the entity's root keeps it from getting soaked; read by the player temperature bridges. */
    public static boolean waterproof(LivingEntity entity) {
        SwimProfile swim = SwimProfile.of(entity);
        return swim != null && swim.style() != SwimStyle.PADDLE;
    }

    /** The wetness that counts for this body. */
    public float soak(float wetness) {
        return waterproof ? 0 : wetness;
    }

    public static ThermalProfile of(LivingEntity entity) {
        float neutral = TemperatureData.DEFAULT_NEUTRAL;
        float band = TemperatureData.DEFAULT_BAND;
        float cold = 1f;
        float heat = 1f;
        boolean suppressed = false;
        for (ThermalToleranceGeneType.Instance gene : ExpressedGenes.instancesOf(entity, ThermalToleranceGeneType.Instance.class)) {
            if (gene.climateAny()) suppressed = true;
            neutral = gene.neutral();
            band = gene.band();
            cold = gene.cold();
            heat = gene.heat();
            break;
        }
        float insulation = 0f;
        boolean sheds = false;
        for (InsulationGeneType.Instance gene : ExpressedGenes.instancesOf(entity, InsulationGeneType.Instance.class)) {
            insulation += gene.amount();
            sheds |= gene.sheds();
        }
        boolean ectotherm = false;
        for (MetabolismGeneType.Instance gene : ExpressedGenes.instancesOf(entity, MetabolismGeneType.Instance.class)) {
            ectotherm |= gene.ectotherm();
        }
        for (AbilityGeneType.Instance gene : ExpressedGenes.instancesOf(entity, AbilityGeneType.Instance.class)) {
            if (gene.ability() == Ability.FIRE_IMMUNITY) heat = 0f;
        }
        return new ThermalProfile(neutral, Math.max(0.1f, band), Math.max(0f, cold), Math.max(0f, heat),
                Math.max(-TemperatureData.CLOTHING_CLAMP, Math.min(TemperatureData.CLOTHING_CLAMP, insulation)),
                sheds, ectotherm, suppressed, waterproof(entity));
    }

    public int neutralTenths() {
        return TemperatureData.tenths(neutral);
    }

    /** True once the body is back inside the comfort band. Relief starts three bands out, so this is the hysteresis. */
    public boolean comfortable(int bodyTenths) {
        return Math.abs(TemperatureData.celsius(bodyTenths) - neutral) <= band + 0.0001f;
    }
}
