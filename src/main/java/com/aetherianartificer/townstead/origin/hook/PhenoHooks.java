package com.aetherianartificer.townstead.origin.hook;

import com.aetherianartificer.townstead.origin.gene.types.ModifierGeneType.Modifier;
import com.aetherianartificer.townstead.origin.modifier.ModifierCapability;
import com.aetherianartificer.townstead.pheno.capability.Capabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The single entry point every vanilla interception (event listener or mixin) funnels through to
 * apply Pheno power effects. The shims stay dumb: all resolution runs through the capability layer
 * here ({@link Capabilities#applyToBase}), so genetics and professions stack with op/priority/
 * provenance and surface in {@code /pheno explain} and {@code /pheno parity}.
 *
 * <p>Each interceptable mechanic still needs its own shim, since vanilla computes them in
 * different places and only some are eventful, but the shims hold no logic. New modifier targets
 * add one semantic method here plus one thin injector in the relevant {@code *PhenoMixin} (or an
 * event listener).</p>
 */
public final class PhenoHooks {

    private PhenoHooks() {}

    // --- generic modifier resolution (apply-to-base over the capability layer) ---

    public static float modifier(LivingEntity e, Modifier target, float base) {
        if (e == null || e.level().isClientSide) return base;
        return (float) Capabilities.applyToBase(e, base, ModifierCapability.key(target));
    }

    public static float modifier(LivingEntity e, Modifier target, @Nullable ResourceLocation discriminator, float base) {
        if (e == null || e.level().isClientSide) return base;
        if (discriminator == null) return modifier(e, target, base);
        return (float) Capabilities.applyToBase(e, base,
                ModifierCapability.key(target), ModifierCapability.key(target, discriminator));
    }

    // --- semantic hooks (named so shims never touch the enum) ---

    public static float heal(LivingEntity e, float amount) { return modifier(e, Modifier.HEALING, amount); }

    public static float damageDealt(LivingEntity e, float amount) { return modifier(e, Modifier.DAMAGE_DEALT, amount); }

    public static float projectileDamage(LivingEntity e, float amount) { return modifier(e, Modifier.PROJECTILE_DAMAGE, amount); }

    public static float breakSpeed(LivingEntity e, float speed) { return modifier(e, Modifier.BREAK_SPEED, speed); }

    public static float exhaustion(Player p, float exhaustion) { return modifier(p, Modifier.EXHAUSTION, exhaustion); }

    public static int xpGain(Player p, int amount) { return Math.round(modifier(p, Modifier.XP_GAIN, amount)); }

    /** Scaled nutrition for an eater (player path). */
    public static int food(LivingEntity e, int nutrition) {
        return Math.max(0, Math.round(modifier(e, Modifier.FOOD, nutrition)));
    }

    /** The food multiplier for an eater (villager path scales its own nutrition by this). */
    public static float foodMultiplier(LivingEntity e) { return modifier(e, Modifier.FOOD, 1f); }

    public static int breedingCooldown(Animal a, int age) { return Math.round(modifier(a, Modifier.BREEDING_COOLDOWN, age)); }

    /** Rescales jump velocity in place; the jump event fires after vanilla sets deltaMovement. */
    public static void jump(LivingEntity e) {
        if (e == null || e.level().isClientSide) return;
        net.minecraft.world.phys.Vec3 m = e.getDeltaMovement();
        float scaled = modifier(e, Modifier.JUMP, (float) m.y);
        if (scaled != (float) m.y) e.setDeltaMovement(m.x, scaled, m.z);
    }

    /**
     * The effect to actually apply, with its duration/amplifier rescaled by the bearer's
     * status_effect_* modifiers (keyed by the effect, so a per-effect and an all-effects modifier
     * fold together). Returns the original instance when nothing changes. Holds the 1.20.1
     * {@code MobEffect} vs 1.21.1 {@code Holder<MobEffect>} delta so the mixin stays trivial.
     */
    public static MobEffectInstance scaleEffect(LivingEntity e, MobEffectInstance effect) {
        if (effect == null || e.level().isClientSide) return effect;
        //? if >=1.21 {
        ResourceLocation id = effect.getEffect().unwrapKey()
                .map(net.minecraft.resources.ResourceKey::location).orElse(null);
        //?} else {
        /*ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
        *///?}
        int duration = effect.getDuration();
        int amplifier = effect.getAmplifier();
        int newDuration = duration < 0 ? duration
                : Math.round(modifier(e, Modifier.STATUS_EFFECT_DURATION, id, duration));
        int newAmplifier = Math.round(modifier(e, Modifier.STATUS_EFFECT_AMPLIFIER, id, amplifier));
        if (newDuration == duration && newAmplifier == amplifier) return effect;
        return new MobEffectInstance(effect.getEffect(), newDuration, Math.max(0, newAmplifier),
                effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }
}
