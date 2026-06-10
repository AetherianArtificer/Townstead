package com.aetherianartificer.townstead.origin.ability;

import com.aetherianartificer.townstead.habitus.power.Power;
import com.aetherianartificer.townstead.habitus.power.PowerComponent;
import com.aetherianartificer.townstead.habitus.power.Powers;
import com.aetherianartificer.townstead.habitus.action.ActionContext;
import com.aetherianartificer.townstead.habitus.condition.ConditionContext;
import com.aetherianartificer.townstead.origin.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.origin.gene.types.ActionOverTimeGeneType;
import com.aetherianartificer.townstead.origin.gene.types.AuraGeneType;
import com.aetherianartificer.townstead.origin.gene.types.EffectImmunityGeneType;
import com.aetherianartificer.townstead.origin.gene.types.GlowGeneType;
import com.aetherianartificer.townstead.origin.gene.types.ParticleGeneType;
import com.aetherianartificer.townstead.origin.gene.types.RestrictEquipmentGeneType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Server-side per-tick enforcement of an entity's expressed passive genes:
 * abilities (effects/movement/flags), glow, ambient particles, and equipment
 * restrictions. Throttled so it runs roughly every {@value #INTERVAL} ticks, spread
 * across entities by id. Called for villagers (tick dispatcher) and players (tick
 * event). Player-only abilities are skipped on villagers.
 */
public final class GeneAbilityTicker {

    private static final int INTERVAL = 10;
    // Kept well above vanilla's 200-tick night-vision flicker threshold so the
    // re-applied effect never enters the "about to expire" fade (which read as a pulse).
    private static final int EFFECT_DURATION = 300;

    private GeneAbilityTicker() {}

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if ((entity.level().getGameTime() + entity.getId()) % INTERVAL != 0) return;

        List<Power> expressed = Powers.active(entity);
        if (expressed.isEmpty()) return;

        boolean isPlayer = entity instanceof Player;
        ConditionContext ctx = new ConditionContext(entity);
        List<GlowGeneType.Instance> glows = new java.util.ArrayList<>();
        List<ParticleGeneType.Instance> particles = new java.util.ArrayList<>();
        List<RestrictEquipmentGeneType.Instance> restricts = new java.util.ArrayList<>();
        List<AuraGeneType.Instance> auras = new java.util.ArrayList<>();
        List<EffectImmunityGeneType.Instance> immunities = new java.util.ArrayList<>();
        List<ActionOverTimeGeneType.Instance> overTime = new java.util.ArrayList<>();
        for (Power gene : expressed) {
            PowerComponent instance = gene.component();
            if (instance instanceof AbilityGeneType.Instance ability) {
                applyAbility(entity, gene.id(), ability, isPlayer, ctx);
            } else if (instance instanceof GlowGeneType.Instance glow) {
                glows.add(glow);
            } else if (instance instanceof ParticleGeneType.Instance particle) {
                particles.add(particle);
            } else if (instance instanceof RestrictEquipmentGeneType.Instance restrict) {
                restricts.add(restrict);
            } else if (instance instanceof AuraGeneType.Instance aura) {
                auras.add(aura);
            } else if (instance instanceof EffectImmunityGeneType.Instance immunity) {
                immunities.add(immunity);
            } else if (instance instanceof ActionOverTimeGeneType.Instance aot) {
                overTime.add(aot);
            }
        }
        applyGlow(entity, glows, ctx);
        applyParticles(entity, particles, ctx);
        applyRestrictions(entity, restricts, ctx);
        applyAuras(entity, auras, ctx);
        applyEffectImmunity(entity, immunities);
        applyActionsOverTime(entity, overTime, ctx);
    }

    private static void applyActionsOverTime(LivingEntity entity, List<ActionOverTimeGeneType.Instance> overTime,
                                             ConditionContext ctx) {
        if (overTime.isEmpty()) return;
        for (ActionOverTimeGeneType.Instance aot : overTime) {
            // tickCount advances ~INTERVAL between passes, so this fires once per the gene's period.
            if (entity.tickCount % aot.interval() >= INTERVAL) continue;
            if (aot.condition() != null && !aot.condition().test(ctx)) continue;
            aot.action().run(new ActionContext(entity));
        }
    }

    private static void applyEffectImmunity(LivingEntity entity, List<EffectImmunityGeneType.Instance> immunities) {
        if (immunities.isEmpty() || entity.getActiveEffects().isEmpty()) return;
        boolean all = false;
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        for (EffectImmunityGeneType.Instance immunity : immunities) {
            if (immunity.all()) all = true;
            ids.addAll(immunity.effects());
        }
        List<MobEffectInstance> toRemove = new java.util.ArrayList<>();
        for (MobEffectInstance active : entity.getActiveEffects()) {
            ResourceLocation id = effectId(active);
            if (all || (id != null && ids.contains(id))) toRemove.add(active);
        }
        for (MobEffectInstance active : toRemove) entity.removeEffect(active.getEffect());
    }

    private static ResourceLocation effectId(MobEffectInstance instance) {
        //? if neoforge {
        return BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());
        //?} else {
        /*return BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect());
        *///?}
    }

    private static void applyAbility(LivingEntity entity, ResourceLocation geneId,
                                     AbilityGeneType.Instance gene, boolean isPlayer, ConditionContext ctx) {
        if (gene.ability().playerOnly() && !isPlayer) return;
        boolean active = gene.mode() == AbilityGeneType.Mode.TOGGLE
                ? AbilityToggles.isOn(entity, geneId)
                : gene.condition() == null || gene.condition().test(ctx);
        if (active) {
            apply(entity, gene.ability());
        } else {
            clear(entity, gene.ability());
        }
    }

    // Undo an effect-backed ability the tick it switches off (toggle released or
    // condition failed), so e.g. night vision ends at once instead of lingering out its
    // renewed duration. Only our own hidden instance is touched, never a potion's.
    private static void clear(LivingEntity entity, Ability ability) {
        switch (ability) {
            case NIGHT_VISION, LAVA_VISION -> removeHidden(entity, MobEffects.NIGHT_VISION);
            case WATER_BREATHING -> removeHidden(entity, MobEffects.WATER_BREATHING);
            case SLOW_FALL -> removeHidden(entity, MobEffects.SLOW_FALLING);
            case INVISIBILITY -> removeHidden(entity, MobEffects.INVISIBILITY);
            case SWIMMING -> removeHidden(entity, MobEffects.DOLPHINS_GRACE);
            case CREATIVE_FLIGHT -> revokeFlight(entity);
            case SPRINTING -> {
                if (entity.isSprinting()) entity.setSprinting(false);
            }
            case HOVER -> entity.setNoGravity(false);
            default -> { }
        }
    }

    private static void removeHidden(LivingEntity entity,
            //? if neoforge {
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect
            //?} else {
            /*net.minecraft.world.effect.MobEffect effect
            *///?}
    ) {
        MobEffectInstance active = entity.getEffect(effect);
        if (active != null && active.isAmbient() && !active.isVisible() && !active.showIcon()) {
            entity.removeEffect(effect);
        }
    }

    private static void revokeFlight(LivingEntity entity) {
        if (entity instanceof Player player && !player.isCreative() && !player.isSpectator()
                && player.getAbilities().mayfly) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    private static void applyAuras(LivingEntity entity, List<AuraGeneType.Instance> auras, ConditionContext ctx) {
        if (auras.isEmpty()) return;
        for (AuraGeneType.Instance aura : auras) {
            // tickCount advances ~INTERVAL between passes, so this fires once per the aura's period.
            if (entity.tickCount % aura.interval() >= INTERVAL) continue;
            if (aura.condition() != null && !aura.condition().test(ctx)) continue;
            var nearby = entity.level().getEntitiesOfClass(LivingEntity.class,
                    entity.getBoundingBox().inflate(aura.radius()));
            for (LivingEntity target : nearby) {
                if (target == entity && !aura.includeSelf()) continue;
                aura.action().run(new ActionContext(target));
            }
        }
    }

    private static void applyParticles(LivingEntity entity, List<ParticleGeneType.Instance> particles,
                                       ConditionContext ctx) {
        if (particles.isEmpty() || !(entity.level() instanceof ServerLevel level)) return;
        for (ParticleGeneType.Instance particle : particles) {
            if (particle.condition() != null && !particle.condition().test(ctx)) continue;
            if (!(BuiltInRegistries.PARTICLE_TYPE.get(particle.particle()) instanceof SimpleParticleType options)) {
                continue;
            }
            // Send per-viewer with the force flag: the broadcast overload hardcodes
            // longDistance=false, which the client's Minimal/Decreased particle setting
            // culls. A racial trait particle should show regardless of that setting.
            double dx = entity.getBbWidth() * particle.spread();
            double dy = entity.getBbHeight() * particle.spread();
            for (net.minecraft.server.level.ServerPlayer viewer : level.players()) {
                if (viewer.distanceToSqr(entity) > 1024.0) continue;
                level.sendParticles(viewer, options, true,
                        entity.getX(), entity.getY(particle.yOffset()), entity.getZ(),
                        particle.count(), dx, dy, dx, particle.speed());
            }
        }
    }

    private static void applyRestrictions(LivingEntity entity, List<RestrictEquipmentGeneType.Instance> restricts,
                                          ConditionContext ctx) {
        if (restricts.isEmpty()) return;
        for (RestrictEquipmentGeneType.Instance restrict : restricts) {
            if (restrict.condition() != null && !restrict.condition().test(ctx)) continue;
            for (EquipmentSlot slot : restrict.slots()) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                entity.setItemSlot(slot, ItemStack.EMPTY);
                if (entity instanceof Player player) {
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                } else {
                    entity.spawnAtLocation(stack);
                }
            }
        }
    }

    private static void applyGlow(LivingEntity entity, List<GlowGeneType.Instance> glows, ConditionContext ctx) {
        if (glows.isEmpty()) return;
        boolean shouldGlow = false;
        for (GlowGeneType.Instance glow : glows) {
            if (glow.condition() == null || glow.condition().test(ctx)) {
                shouldGlow = true;
                break;
            }
        }
        if (entity.hasGlowingTag() != shouldGlow) entity.setGlowingTag(shouldGlow);
    }

    private static void apply(LivingEntity entity, Ability ability) {
        switch (ability) {
            case NIGHT_VISION -> entity.addEffect(hidden(MobEffects.NIGHT_VISION));
            case WATER_BREATHING -> {
                if (entity.isUnderWater()) entity.addEffect(hidden(MobEffects.WATER_BREATHING));
            }
            case SLOW_FALL -> {
                if (!entity.onGround() && entity.getDeltaMovement().y < 0) entity.addEffect(hidden(MobEffects.SLOW_FALLING));
            }
            case LAVA_VISION -> {
                if (entity.isInLava()) entity.addEffect(hidden(MobEffects.NIGHT_VISION));
            }
            case FIRE_IMMUNITY -> {
                if (entity.getRemainingFireTicks() > 0) entity.setRemainingFireTicks(0);
            }
            case INVISIBILITY -> entity.addEffect(hidden(MobEffects.INVISIBILITY));
            case SWIMMING -> entity.addEffect(hidden(MobEffects.DOLPHINS_GRACE));
            case CLIMBING -> {
                if (entity.horizontalCollision) {
                    Vec3 m = entity.getDeltaMovement();
                    entity.setDeltaMovement(m.x, 0.2, m.z);
                    entity.fallDistance = 0f;
                }
            }
            case CREATIVE_FLIGHT -> {
                if (entity instanceof Player player && !player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
            }
            case SPRINTING -> {
                if (!entity.isSprinting()) entity.setSprinting(true);
            }
            case HOVER -> {
                entity.setNoGravity(true);
                Vec3 m = entity.getDeltaMovement();
                if (m.y < 0) entity.setDeltaMovement(m.x, 0, m.z);
                entity.fallDistance = 0f;
            }
        }
    }

    /**
     * Break-speed multiplier for {@code aerial_affinity}: cancels vanilla's 5x airborne
     * mining penalty so the player breaks blocks at normal speed off the ground. Called
     * from the {@code BreakSpeed} event after other modifiers.
     */
    public static float aerialBreakSpeed(Player player, float current) {
        if (!player.onGround() && Abilities.isActive(player, Ability.AERIAL_AFFINITY)) {
            return current * 5f;
        }
        return current;
    }

    // A short, ambient, particle-less, icon-less effect re-added each interval. The
    // MobEffects field type (MobEffect on 1.20.1, Holder on 1.21.1) matches the
    // constructor on both branches, so no version guard is needed.
    private static MobEffectInstance hidden(
            //? if neoforge {
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect
            //?} else {
            /*net.minecraft.world.effect.MobEffect effect
            *///?}
    ) {
        return new MobEffectInstance(effect, EFFECT_DURATION, 0, true, false, false);
    }
}
