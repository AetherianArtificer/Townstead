package com.aetherianartificer.townstead.root.rig;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.gene.types.ScaleGeneType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The whole-body size factor from an entity's {@code pheno:scale} genes, and the upkeep that makes
 * it real. On 1.21 the factor drives the vanilla {@code generic.scale} attribute, which already
 * scales the model, hitbox and eye height everywhere (and {@link RigHitboxes} folds it in). On
 * 1.20.1 there is no such attribute: {@link RigHitboxes} and the size event read {@link #of}
 * directly on both sides, and the client scales the model while rendering.
 *
 * <p>The optional {@code reach} and {@code speed} parts are transient attribute modifiers kept in
 * step by {@link #tick}, like attribute genes.</p>
 */
public final class BodyScale {

    private static final int INTERVAL = 10;

    private record Resolved(float scale, boolean reach, boolean speed) {}

    private static final Map<LivingEntity, Float> LAST = new WeakHashMap<>();

    /** True on versions without the vanilla scale attribute, where Townstead sizes the body itself. */
    //? if >=1.21 {
    public static final boolean LEGACY = false;
    //?} else {
    /*public static final boolean LEGACY = true;
    *///?}

    //? if >=1.21 {
    private static final net.minecraft.resources.ResourceLocation SCALE_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "body_scale");
    private static final net.minecraft.resources.ResourceLocation REACH_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "body_scale_reach");
    private static final net.minecraft.resources.ResourceLocation SPEED_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "body_scale_speed");
    //?} else {
    /*private static final java.util.UUID REACH_ID = java.util.UUID.nameUUIDFromBytes("townstead:body_scale_reach".getBytes());
    private static final java.util.UUID ENTITY_REACH_ID = java.util.UUID.nameUUIDFromBytes("townstead:body_scale_entity_reach".getBytes());
    private static final java.util.UUID SPEED_ID = java.util.UUID.nameUUIDFromBytes("townstead:body_scale_speed".getBytes());
    *///?}

    private BodyScale() {}

    /** The product of the entity's expressed scale genes, on either side; 1 without any. */
    public static float of(LivingEntity entity) {
        return resolve(entity).scale();
    }

    private static Resolved resolve(LivingEntity entity) {
        if (entity == null || entity.level() == null || !ExpressedGenes.canCarry(entity)) return new Resolved(1f, false, false);
        float scale = 1f;
        boolean reach = false, speed = false;
        if (entity.level().isClientSide) {
            for (String geneId : com.aetherianartificer.townstead.client.root.RootClientStore.expressedGenes(entity)) {
                GeneCatalogEntry gene = com.aetherianartificer.townstead.client.root.RootCatalogClient.gene(geneId);
                if (gene != null && gene.isBodyScale()) scale *= gene.bodyScale();
            }
        } else {
            for (ScaleGeneType.Instance gene : ExpressedGenes.instancesOf(entity, ScaleGeneType.Instance.class)) {
                scale *= gene.scale();
                reach |= gene.reach();
                speed |= gene.speed();
            }
        }
        return new Resolved(Math.max(ScaleGeneType.MIN, Math.min(ScaleGeneType.MAX, scale)), reach, speed);
    }

    /** Living tick: keep modifiers in step and resize the body when the factor changes. */
    public static void tick(LivingEntity entity) {
        if ((entity.tickCount + entity.getId()) % INTERVAL != 0 || !ExpressedGenes.canCarry(entity)) return;
        Resolved resolved = resolve(entity);
        if (!entity.level().isClientSide) apply(entity, resolved);
        // 1.21 resizes through the scale attribute; 1.20.1 must refresh the box itself.
        if (!LEGACY) return;
        Float previous = LAST.put(entity, resolved.scale());
        if (previous == null ? resolved.scale() != 1f : Math.abs(previous - resolved.scale()) > 1.0e-4f) {
            entity.refreshDimensions();
        }
    }

    private static void apply(LivingEntity entity, Resolved resolved) {
        double factor = resolved.scale() - 1.0;
        //? if >=1.21 {
        converge(entity.getAttribute(Attributes.SCALE), SCALE_ID, factor);
        converge(entity.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), REACH_ID, resolved.reach() ? factor : 0);
        converge(entity.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), REACH_ID, resolved.reach() ? factor : 0);
        converge(entity.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, resolved.speed() ? factor : 0);
        //?} else {
        /*converge(entity.getAttribute(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get()), REACH_ID,
                resolved.reach() ? factor : 0);
        converge(entity.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()), ENTITY_REACH_ID,
                resolved.reach() ? factor : 0);
        converge(entity.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, resolved.speed() ? factor : 0);
        *///?}
    }

    //? if >=1.21 {
    private static void converge(AttributeInstance attribute, net.minecraft.resources.ResourceLocation id, double amount) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(id);
        if (current != null && Math.abs(current.amount() - amount) < 1.0e-6) return;
        if (current != null) attribute.removeModifier(id);
        if (Math.abs(amount) > 1.0e-6) {
            attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
    //?} else {
    /*private static void converge(AttributeInstance attribute, java.util.UUID id, double amount) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(id);
        if (current != null && Math.abs(current.getAmount() - amount) < 1.0e-6) return;
        if (current != null) attribute.removeModifier(id);
        if (Math.abs(amount) > 1.0e-6) {
            attribute.addTransientModifier(new AttributeModifier(id, "townstead:body_scale", amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
    *///?}
}
