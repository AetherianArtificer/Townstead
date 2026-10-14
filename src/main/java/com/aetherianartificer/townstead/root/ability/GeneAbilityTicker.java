package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.PowerComponent;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.ActionOverTimeGeneType;
import com.aetherianartificer.townstead.root.gene.types.AuraGeneType;
import com.aetherianartificer.townstead.root.gene.types.EffectImmunityGeneType;
import com.aetherianartificer.townstead.root.gene.types.FlightSpeedGeneType;
import com.aetherianartificer.townstead.root.gene.types.GlowGeneType;
import com.aetherianartificer.townstead.root.gene.types.ParticleGeneType;
import com.aetherianartificer.townstead.root.gene.types.RestrictEquipmentGeneType;
import com.aetherianartificer.townstead.root.gene.types.ScareMobGeneType;
import com.aetherianartificer.townstead.root.hazard.Hazard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
    // Vanilla's Abilities default. Multipliers resolve against this constant rather than the
    // live value so repeated passes can't compound, and so a restore lands back on vanilla.
    private static final float BASE_FLYING_SPEED = 0.05f;
    // Kept well above vanilla's 200-tick night-vision flicker threshold so the
    // re-applied effect never enters the "about to expire" fade (which read as a pulse).
    private static final int EFFECT_DURATION = 300;

    private GeneAbilityTicker() {}

    /**
     * Clears every persistent passive-ability state this ticker can set (the hidden
     * effects, the no-gravity / sprinting / fly flags, and the glow tag). Called on an
     * origin change: the convergent {@link #tick} only ever clears a gene it is still
     * iterating, so a gene that leaves the expressed set would otherwise orphan its
     * applied state. {@code clear} is idempotent, so a blanket pass is safe; the next
     * tick re-applies whatever the new origin grants.
     */
    public static void resetPassives(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        for (Ability ability : Ability.values()) clear(entity, ability);
        if (entity instanceof Player player) setFlyingSpeed(player, BASE_FLYING_SPEED);
        if (entity.hasGlowingTag()) entity.setGlowingTag(false);
        // Clear hazard path-avoidance; the next tick re-applies whatever the new origin's genes imply.
        if (entity instanceof PathfinderMob mob) {
            Hazard.clearProactive(mob);
            com.aetherianartificer.townstead.root.hazard.HazardPaths.clear(mob);
        }
    }

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if ((entity.level().getGameTime() + entity.getId()) % INTERVAL != 0) return;

        com.aetherianartificer.townstead.root.GeneExpressionSync.tick(entity);
        List<Power> expressed = Powers.active(entity);
        if (expressed.isEmpty()) return;

        boolean isPlayer = entity instanceof Player;
        ConditionContext ctx = new ConditionContext(entity);
        List<GlowGeneType.Instance> glows = new java.util.ArrayList<>();
        List<ParticleGeneType.Instance> particles = new java.util.ArrayList<>();
        List<RestrictEquipmentGeneType.Instance> restricts = new java.util.ArrayList<>();
        List<AuraGeneType.Instance> auras = new java.util.ArrayList<>();
        List<EffectImmunityGeneType.Instance> immunities = new java.util.ArrayList<>();
        List<FlightSpeedGeneType.Instance> flightSpeeds = new java.util.ArrayList<>();
        List<ActionOverTimeGeneType.Instance> overTime = new java.util.ArrayList<>();
        List<ScareMobGeneType.Instance> scares = new java.util.ArrayList<>();
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
            } else if (instance instanceof FlightSpeedGeneType.Instance flightSpeed) {
                flightSpeeds.add(flightSpeed);
            } else if (instance instanceof ScareMobGeneType.Instance scare) {
                scares.add(scare);
            } else if (instance instanceof com.aetherianartificer.townstead.root.gene.types.StackingEffectGeneType.Instance stacking) {
                com.aetherianartificer.townstead.root.effect.StackingEffects.apply(entity, gene.id(), stacking, ctx);
            }
        }
        applyGlow(entity, glows, ctx);
        applyParticles(entity, particles, ctx);
        applyRestrictions(entity, restricts, ctx);
        applyAuras(entity, auras, ctx);
        applyEffectImmunity(entity, immunities);
        applyFlightSpeed(entity, flightSpeeds, ctx);
        applyActionsOverTime(entity, overTime, ctx);
        applyHazardAvoidance(entity, overTime, ctx);
        applyScare(entity, scares, ctx);
    }

    /**
     * Keeps an entity clear of the places its own genes say hurt it (see {@link Hazard}). Proactive:
     * a sun hazard keeps the pathfinder off sunlit tiles. Reactive: caught in a hazard, head for the
     * nearest safe block; heading somewhere a hazard covers, pick a safe block instead; standing at
     * the edge of one, wander the safe side rather than idle. Brain-based villagers are steered
     * through WALK_TARGET; players are not pathfinders, so they simply take the effect.
     */
    private static void applyHazardAvoidance(LivingEntity entity, List<ActionOverTimeGeneType.Instance> overTime,
                                             ConditionContext ctx) {
        if (overTime.isEmpty() || !(entity instanceof PathfinderMob mob)) return;
        List<Hazard> hazards = new java.util.ArrayList<>(2);
        boolean inDanger = false;
        for (ActionOverTimeGeneType.Instance aot : overTime) {
            Hazard hazard = aot.hazard();
            if (hazard == null) continue;
            hazards.add(hazard);
            hazard.installProactive(mob, ctx);
            if (aot.condition() == null || aot.condition().test(ctx)) inDanger = true;
        }
        com.aetherianartificer.townstead.root.hazard.HazardPaths.update(mob, hazards, ctx);
        if (!hazards.isEmpty()) steerToSafety(mob, ctx, hazards, inDanger);
    }

    private static void steerToSafety(PathfinderMob mob, ConditionContext ctx, List<Hazard> hazards, boolean inDanger) {
        var brain = mob.getBrain();
        int radius = 0;
        float flee = 0f, roam = 0f;
        for (Hazard hazard : hazards) {
            radius = Math.max(radius, hazard.searchRadius());
            flee = Math.max(flee, hazard.fleeSpeed());
            roam = Math.max(roam, hazard.roamSpeed());
        }
        if (inDanger) {
            BlockPos safe = safeSpot(mob, ctx, hazards, radius);
            if (safe != null) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(safe, flee, 0));
            return;
        }
        var target = brain.getMemory(MemoryModuleType.WALK_TARGET);
        if (target.isPresent()) {
            // Somewhere to be already: only step in when that somewhere is inside a hazard.
            BlockPos destination = target.get().getTarget().currentBlockPosition();
            for (Hazard hazard : hazards) {
                if (!hazard.rejectDestinations() || !hazard.dangerousAt(ctx, destination)) continue;
                BlockPos safe = safeSpot(mob, ctx, hazards, radius);
                if (safe != null) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(safe, roam, 1));
                else brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                return;
            }
            return;
        }
        // Idle at the edge of a hazard (a cave mouth, tree shade, a garlic boundary): roam the safe
        // side. Deep in cover nothing is near, so it acts normally and its pathing keeps it there.
        for (Hazard hazard : hazards) {
            if (!hazard.roam() || !nearExposure(mob, ctx, hazard)) continue;
            BlockPos spot = safeSpot(mob, ctx, hazards, radius);
            if (spot != null) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, roam, 1));
            return;
        }
    }

    /** True when a block within the hazard's edge distance is dangerous, i.e. the mob is at an edge. */
    private static boolean nearExposure(PathfinderMob mob, ConditionContext ctx, Hazard hazard) {
        BlockPos base = mob.blockPosition();
        int d = hazard.edgeDistance();
        for (int dx = -d; dx <= d; dx += d) {
            for (int dz = -d; dz <= d; dz += d) {
                if ((dx != 0 || dz != 0) && hazard.dangerousAt(ctx, base.offset(dx, 0, dz))) return true;
            }
        }
        return false;
    }

    /**
     * A stand-able block safe from every hazard, searching near first and then out to {@code radius},
     * or null when a dozen tries find none.
     */
    private static BlockPos safeSpot(PathfinderMob mob, ConditionContext ctx, List<Hazard> hazards, int radius) {
        Level level = mob.level();
        BlockPos origin = mob.blockPosition();
        for (int i = 0; i < 12; i++) {
            int reach = i < 6 ? Math.max(2, radius / 2) : radius;
            BlockPos pos = origin.offset(mob.getRandom().nextInt(reach * 2 + 1) - reach,
                    mob.getRandom().nextInt(7) - 3, mob.getRandom().nextInt(reach * 2 + 1) - reach);
            if (!level.isLoaded(pos) || !level.isEmptyBlock(pos) || level.isEmptyBlock(pos.below())) continue;
            boolean allSafe = true;
            for (Hazard hazard : hazards) {
                if (hazard.dangerousAt(ctx, pos)) { allSafe = false; break; }
            }
            if (allSafe) return pos;
        }
        return null;
    }

    private static void applyScare(LivingEntity entity, List<ScareMobGeneType.Instance> scares,
                                   ConditionContext ctx) {
        if (scares.isEmpty()) return;
        for (ScareMobGeneType.Instance scare : scares) {
            if (scare.condition() != null && !scare.condition().test(ctx)) continue;
            var nearby = entity.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    entity.getBoundingBox().inflate(scare.radius()));
            for (net.minecraft.world.entity.Mob mob : nearby) {
                if (mob == entity || !scare.matches(mob)) continue;
                if (mob.getTarget() == entity) mob.setTarget(null);
                Vec3 away = mob.position().subtract(entity.position());
                if (away.lengthSqr() < 1.0e-4) continue;
                away = away.normalize().scale(scare.radius());
                mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY() + away.y, mob.getZ() + away.z, 1.3);
            }
        }
    }

    private static void applyActionsOverTime(LivingEntity entity, List<ActionOverTimeGeneType.Instance> overTime,
                                             ConditionContext ctx) {
        if (overTime.isEmpty()) return;
        for (ActionOverTimeGeneType.Instance aot : overTime) {
            // tickCount advances ~INTERVAL between passes, so this fires once per the gene's period.
            if (entity.tickCount % aot.interval() >= INTERVAL) continue;
            if (aot.condition() != null && !aot.condition().test(ctx)) continue;
            if (aot.costResource() != null
                    && ResourceValues.get(entity, aot.costResource()) < aot.costAmount()) continue;
            aot.action().run(new ActionContext(entity));
            if (aot.costResource() != null && aot.costAmount() > 0) {
                ResourceValues.change(entity, aot.costResource(), -aot.costAmount());
            }
        }
    }

    /**
     * Resolves every expressed flight-speed gene into one multiplier over vanilla's base and writes
     * it to the player's abilities, which sync to the client. Creative flight is client-paced, so a
     * synced speed is the only thing that slows it cleanly; a server-side impulse gets overwritten by
     * the next movement packet. Runs on the ticker's own cadence rather than per physics tick because
     * this sets a value the client reads, not a force that has to land on a particular tick.
     *
     * <p>A player with no such gene is never touched, so another mod's fly speed survives. While a
     * gene is present but its condition is false the multiplier resolves to 1, which restores the
     * vanilla base.</p>
     */
    private static void applyFlightSpeed(LivingEntity entity, List<FlightSpeedGeneType.Instance> speeds,
                                         ConditionContext ctx) {
        if (speeds.isEmpty() || !(entity instanceof Player player)) return;
        float multiplier = 1f;
        for (FlightSpeedGeneType.Instance speed : speeds) {
            if (speed.condition() != null && !speed.condition().test(ctx)) continue;
            multiplier *= speed.multiplier();
        }
        setFlyingSpeed(player, BASE_FLYING_SPEED * multiplier);
    }

    private static void setFlyingSpeed(Player player, float speed) {
        if (Math.abs(player.getAbilities().getFlyingSpeed() - speed) < 1.0e-5f) return;
        player.getAbilities().setFlyingSpeed(speed);
        player.onUpdateAbilities();
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
                ? AbilityToggles.isOn(entity, geneId, gene.defaultOn())
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
            case HIGH_JUMP -> removeHidden(entity, MobEffects.JUMP);
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
            if (aura.costResource() != null
                    && ResourceValues.get(entity, aura.costResource()) < aura.costAmount()) {
                continue;
            }
            var nearby = entity.level().getEntitiesOfClass(LivingEntity.class,
                    entity.getBoundingBox().inflate(aura.radius()));
            boolean pulsed = false;
            for (LivingEntity target : nearby) {
                if (target == entity && !aura.includeSelf()) continue;
                if (!auraTargets(aura.target(), target)) continue;
                aura.action().run(new ActionContext(target));
                pulsed = true;
            }
            if (pulsed && aura.costResource() != null) {
                ResourceValues.change(entity, aura.costResource(), -aura.costAmount());
            }
        }
    }

    private static boolean auraTargets(String target, LivingEntity candidate) {
        return switch (target) {
            case "hostile" -> candidate instanceof net.minecraft.world.entity.monster.Enemy;
            case "non_hostile" -> !(candidate instanceof net.minecraft.world.entity.monster.Enemy);
            default -> true;
        };
    }

    private static void applyParticles(LivingEntity entity, List<ParticleGeneType.Instance> particles,
                                       ConditionContext ctx) {
        if (particles.isEmpty() || !(entity.level() instanceof ServerLevel level)) return;
        for (ParticleGeneType.Instance particle : particles) {
            if (particle.condition() != null && !particle.condition().test(ctx)) continue;
            net.minecraft.core.particles.ParticleOptions options = com.aetherianartificer.townstead.pheno.particle.DataParticles.options(particle.particle());
            if (options == null) continue;
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
            // Jump Boost II: a vanilla effect, so the owning client's jump physics picks it
            // up through the normal effect sync (no client mirror needed).
            case HIGH_JUMP -> entity.addEffect(hidden(MobEffects.JUMP, 1));
            case WATER_BREATHING -> {
                if (entity.isUnderWater()) entity.addEffect(hidden(MobEffects.WATER_BREATHING));
            }
            case SLOW_FALL -> {
                // Stands down during a glide: Slow Falling clamps the dive speed elytra
                // physics steers with, turning the glide into an unsteerable drift.
                // A parachute keeps hops normal and slows only a real drop (ParachutePhysics).
                if (entity.isFallFlying() || com.aetherianartificer.townstead.root.locomotion.ParachutePhysics
                        .isParachutist(entity)) {
                    removeHidden(entity, MobEffects.SLOW_FALLING);
                } else if (!entity.onGround() && entity.getDeltaMovement().y < 0) {
                    entity.addEffect(hidden(MobEffects.SLOW_FALLING));
                }
            }
            case LAVA_VISION -> {
                if (entity.isInLava()) entity.addEffect(hidden(MobEffects.NIGHT_VISION));
            }
            case FIRE_IMMUNITY -> {
                if (entity.getRemainingFireTicks() > 0) entity.setRemainingFireTicks(0);
            }
            case INVISIBILITY -> entity.addEffect(hidden(MobEffects.INVISIBILITY));
            case SWIMMING -> entity.addEffect(hidden(MobEffects.DOLPHINS_GRACE));
            // CLIMBING is handled per physics tick by LivingEntityClimbMixin (onClimbable), not here:
            // a once-per-interval velocity nudge can't overcome gravity, so it lived in the wrong clock.
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
                if (com.aetherianartificer.townstead.root.locomotion.DriftPhysics.isDrifter(entity)) {
                    // Drift floats over the ground instead of holding altitude: the drift
                    // pilot (villagers) or the controlling client (players) moves the body.
                    if (entity instanceof Player && entity.isNoGravity()) entity.setNoGravity(false);
                    entity.fallDistance = 0f;
                    return;
                }
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
        return hidden(effect, 0);
    }

    private static MobEffectInstance hidden(
            //? if neoforge {
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
            //?} else {
            /*net.minecraft.world.effect.MobEffect effect,
            *///?}
            int amplifier
    ) {
        return new MobEffectInstance(effect, EFFECT_DURATION, amplifier, true, false, false);
    }
}
