package com.aetherianartificer.townstead.origin.attribute;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.origin.gene.types.AttributeGeneType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
//? if neoforge {
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
//?} else {
/*import net.minecraft.world.entity.ai.attributes.Attribute;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
*///?}

/**
 * Applies a villager or player's expressed {@code attribute} genes as transient
 * attribute modifiers. A converging tick keeps each modifier present exactly while
 * its gene is active (always, or while its condition holds), keyed by a stable id
 * derived from the gene id so it is idempotent. Transient modifiers don't persist,
 * so they self-heal on reload. Throttled like the ability ticker.
 */
public final class GeneAttributeApplier {

    private static final int INTERVAL = 10;

    private GeneAttributeApplier() {}

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if ((entity.level().getGameTime() + entity.getId()) % INTERVAL != 0) return;

        ConditionContext ctx = new ConditionContext(entity);
        for (Power gene : Powers.active(entity)) {
            if (gene.component() instanceof AttributeGeneType.Instance instance && instance.attribute() != null) {
                applyOne(entity, gene.id(), instance, ctx);
            }
        }
    }

    /**
     * Removes the modifiers for a set of (old) attribute genes. Called on an origin
     * change with the entity's previously-expressed genes: the convergent {@link #tick}
     * only removes a modifier while still iterating its gene, so a gene that leaves the
     * expressed set would orphan its modifier until reload. The next tick re-adds
     * whatever the new origin grants.
     */
    public static void removeFor(LivingEntity entity, java.util.List<Power> oldGenes) {
        if (entity.level().isClientSide) return;
        for (Power gene : oldGenes) {
            if (gene.component() instanceof AttributeGeneType.Instance instance && instance.attribute() != null) {
                removeOne(entity, gene.id(), instance);
            }
        }
    }

    private static void removeOne(LivingEntity entity, ResourceLocation geneId, AttributeGeneType.Instance instance) {
        //? if neoforge {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE
                .getHolder(ResourceKey.create(Registries.ATTRIBUTE, instance.attribute())).orElse(null);
        if (holder == null) return;
        AttributeInstance attr = entity.getAttribute(holder);
        if (attr != null) attr.removeModifier(modifierId(geneId));
        //?} else {
        /*Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(instance.attribute());
        if (attribute == null) return;
        AttributeInstance attr = entity.getAttribute(attribute);
        if (attr != null) attr.removeModifier(modifierUuid(geneId));
        *///?}
    }

    private static void applyOne(LivingEntity entity, ResourceLocation geneId,
                                 AttributeGeneType.Instance instance, ConditionContext ctx) {
        boolean active = instance.condition() == null || instance.condition().test(ctx);
        //? if neoforge {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE
                .getHolder(ResourceKey.create(Registries.ATTRIBUTE, instance.attribute())).orElse(null);
        if (holder == null) return;
        AttributeInstance attr = entity.getAttribute(holder);
        if (attr == null) return;
        ResourceLocation modId = modifierId(geneId);
        boolean present = attr.getModifier(modId) != null;
        if (active && !present) {
            attr.addTransientModifier(new AttributeModifier(modId, instance.amount(), operation(instance.operation())));
        } else if (!active && present) {
            attr.removeModifier(modId);
        }
        //?} else {
        /*Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(instance.attribute());
        if (attribute == null) return;
        AttributeInstance attr = entity.getAttribute(attribute);
        if (attr == null) return;
        UUID modId = modifierUuid(geneId);
        boolean present = attr.getModifier(modId) != null;
        if (active && !present) {
            attr.addTransientModifier(new AttributeModifier(modId, "townstead_origins:" + geneId,
                    instance.amount(), operation(instance.operation())));
        } else if (!active && present) {
            attr.removeModifier(modId);
        }
        *///?}
    }

    private static AttributeModifier.Operation operation(AttributeGeneType.Op op) {
        //? if neoforge {
        return switch (op) {
            case MULTIPLY_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case MULTIPLY_TOTAL -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
        //?} else {
        /*return switch (op) {
            case MULTIPLY_BASE -> AttributeModifier.Operation.MULTIPLY_BASE;
            case MULTIPLY_TOTAL -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> AttributeModifier.Operation.ADDITION;
        };
        *///?}
    }

    //? if neoforge {
    private static ResourceLocation modifierId(ResourceLocation geneId) {
        return ResourceLocation.fromNamespaceAndPath("townstead_origins",
                "gene/" + geneId.getNamespace() + "/" + geneId.getPath());
    }
    //?} else {
    /*private static UUID modifierUuid(ResourceLocation geneId) {
        return UUID.nameUUIDFromBytes(("townstead_origins:gene/" + geneId).getBytes(StandardCharsets.UTF_8));
    }
    *///?}
}
