package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.types.WadeGeneType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.material.Fluid;
//? if neoforge {
import net.minecraft.resources.ResourceLocation;
//?} else {
/*import java.nio.charset.StandardCharsets;
import java.util.UUID;
*///?}

import java.util.List;

/**
 * Keeps a {@code wade} gene's {@code movement_speed} penalty present exactly while the bearer
 * stands in a listed fluid, as a transient modifier (self-heals on reload, never persisted).
 * Server-side; the attribute change syncs to the owning client, so a player predicts the slower
 * speed without extra plumbing. Convergent and idempotent, mirroring {@code GeneAttributeApplier}.
 */
public final class Wade {

    private Wade() {}

    //? if neoforge {
    private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("townstead", "wade");
    //?} else {
    /*private static final UUID MODIFIER_ID =
            UUID.nameUUIDFromBytes("townstead:wade".getBytes(StandardCharsets.UTF_8));
    *///?}

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if (!ExpressedGenes.canCarry(entity)) return;
        AttributeInstance attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        boolean present = attr.getModifier(MODIFIER_ID) != null;

        Float speed = (entity.isInWater() || entity.isInLava()) ? wadeSpeed(entity) : null;
        boolean apply = speed != null && speed != 1f;

        if (apply && !present) {
            attr.addTransientModifier(modifier(speed - 1f));
        } else if (!apply && present) {
            attr.removeModifier(MODIFIER_ID);
        }
    }

    /** The wade speed fraction for the fluid the entity is currently in, or null if none applies. */
    private static Float wadeSpeed(LivingEntity entity) {
        List<WadeGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, WadeGeneType.Instance.class);
        for (WadeGeneType.Instance gene : genes) {
            for (TagKey<Fluid> fluid : gene.fluids()) {
                if (entity.getFluidHeight(fluid) > 0) return gene.speed();
            }
        }
        return null;
    }

    private static AttributeModifier modifier(double amount) {
        //? if neoforge {
        return new AttributeModifier(MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        //?} else {
        /*return new AttributeModifier(MODIFIER_ID, "townstead:wade", amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        *///?}
    }
}
