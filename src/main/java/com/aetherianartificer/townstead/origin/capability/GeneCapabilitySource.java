package com.aetherianartificer.townstead.origin.capability;

import com.aetherianartificer.townstead.origin.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.origin.gene.types.ModifierGeneType;
import com.aetherianartificer.townstead.origin.modifier.ModifierCapability;
import com.aetherianartificer.townstead.pheno.capability.CapabilityCollector;
import com.aetherianartificer.townstead.pheno.capability.CapabilityKey;
import com.aetherianartificer.townstead.pheno.capability.CapabilitySource;
import com.aetherianartificer.townstead.pheno.capability.Provenance;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.PowerComponent;
import com.aetherianartificer.townstead.pheno.power.Powers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Reflects an entity's expressed genes into capability contributions, keyed by gene id for
 * provenance. This is a read-side observer: it does not change how the gene appliers run, so it
 * is safe to add before any applier migrates onto the capability layer. It gives
 * {@code /pheno explain} real genetics provenance today.
 *
 * <p>Currently surfaces {@code ability/*} flags and {@code modifier/*} scalars. Attribute and
 * damage genes (whose per-gene operation mix needs a per-key identity decision) migrate as the
 * appliers themselves move onto the layer.
 */
public final class GeneCapabilitySource implements CapabilitySource {

    @Override
    public void contribute(LivingEntity entity, CapabilityCollector out) {
        ConditionContext ctx = new ConditionContext(entity);
        for (Power power : Powers.active(entity)) {
            PowerComponent component = power.component();
            if (component instanceof AbilityGeneType.Instance ability) {
                CapabilityKey key = CapabilityKey.flag(id("ability/" + ability.ability().key()));
                boolean active = ability.condition() == null || ability.condition().test(ctx);
                out.flag(key, Provenance.gene(power.id(), ability.mode().name().toLowerCase(Locale.ROOT)), active);
            } else if (component instanceof ModifierGeneType.Instance modifier) {
                CapabilityKey key = ModifierCapability.key(modifier.modifier(), modifier.discriminator());
                boolean active = modifier.condition() == null || modifier.condition().test(ctx);
                for (ModifierGeneType.Mod mod : modifier.mods()) {
                    out.numeric(key, ModifierCapability.op(mod.op()), mod.value(),
                            Provenance.gene(power.id()), active);
                }
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse("townstead_origins:" + path);
    }
}
