package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
import com.aetherianartificer.townstead.root.gene.types.ActiveAbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.ResourceGeneType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * How much height a flier can still buy with its wings: the style's {@code fullLift}
 * scaled by how full its flight resources are. The flight resources are the ones the
 * flight gene declares itself (a {@code wingbeats} bar) plus any resource a
 * {@code when_flying} active ability costs; the emptiest one sets the fraction.
 * Regeneration during the trip is not counted, so the estimate errs short.
 */
public final class FlightStamina {

    private FlightStamina() {}

    /** Blocks of lift available now: 0 for a pure glider, infinite when flight costs nothing. */
    public static double liftBudget(LivingEntity entity, FlightProfile profile) {
        double full = profile.style().fullLift();
        if (full <= 0 || Double.isInfinite(full)) return full;
        double fraction = fraction(entity, profile);
        return Double.isNaN(fraction) ? Double.POSITIVE_INFINITY : full * fraction;
    }

    /** Fullness of the emptiest flight resource, 0 to 1; NaN when flight has no resource. */
    public static double fraction(LivingEntity entity, FlightProfile profile) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        ResourceLocation gene = profile.power().id();
        String prefix = gene.getPath() + "/";
        for (Power power : Powers.active(entity)) {
            if (power.component() instanceof ResourceGeneType.Instance
                    && power.id().getNamespace().equals(gene.getNamespace())
                    && power.id().getPath().startsWith(prefix)) {
                ids.add(power.id());
            }
            if (power.component() instanceof ActiveAbilityGeneType.Instance active
                    && active.aiTrigger() == ActiveAbilityGeneType.AiTrigger.WHEN_FLYING
                    && active.costResource() != null) {
                ids.add(active.costResource());
            }
        }
        double lowest = Double.NaN;
        for (ResourceLocation id : ids) {
            ResourceGeneType.Instance resource = resource(entity, id);
            if (resource == null || resource.max() <= resource.min()) continue;
            double f = (double) (ResourceValues.get(entity, id) - resource.min()) / (resource.max() - resource.min());
            if (Double.isNaN(lowest) || f < lowest) lowest = f;
        }
        return lowest;
    }

    private static ResourceGeneType.Instance resource(LivingEntity entity, ResourceLocation id) {
        for (Power power : Powers.active(entity)) {
            if (power.id().equals(id) && power.component() instanceof ResourceGeneType.Instance instance) return instance;
        }
        return null;
    }
}
