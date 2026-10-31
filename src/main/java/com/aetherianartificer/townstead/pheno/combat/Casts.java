package com.aetherianartificer.townstead.pheno.combat;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Wind-ups of active abilities: the gap between pressing an ability and its effect, during which
 * the caster may be slowed and, if the ability says so, interrupted by taking damage. One cast per
 * entity at a time; the ability layer decides what completing or interrupting means.
 */
public final class Casts {

    /** How an ability winds up. {@code slow} is the movement speed kept while casting (1 = none lost). */
    public record Windup(int ticks, float slow, boolean interruptible,
                         @Nullable Action onStart, @Nullable Action onInterrupt) {}

    /** What finishing or interrupting a cast should do, supplied by the ability layer. */
    public interface Listener {
        void complete(LivingEntity caster);

        void interrupted(LivingEntity caster);
    }

    private record Cast(Windup windup, long completesAt, Listener listener) {}

    private static final Map<LivingEntity, Cast> CASTS = new WeakHashMap<>();

    //? if >=1.21 {
    private static final ResourceLocation SLOW_ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "windup_slow");
    //?} else {
    /*private static final UUID SLOW_ID = UUID.nameUUIDFromBytes("townstead:windup_slow".getBytes());
    *///?}

    private Casts() {}

    public static boolean isCasting(LivingEntity entity) {
        return CASTS.containsKey(entity);
    }

    /** Begin winding up; false when the entity is already casting. */
    public static boolean begin(LivingEntity caster, Windup windup, Listener listener) {
        if (CASTS.containsKey(caster)) return false;
        CASTS.put(caster, new Cast(windup, caster.level().getGameTime() + windup.ticks(), listener));
        applySlow(caster, windup.slow());
        if (windup.onStart() != null) windup.onStart().run(new ActionContext(caster));
        return true;
    }

    /** Living tick: complete casts whose time has come. */
    public static void tick(LivingEntity caster) {
        Cast cast = CASTS.get(caster);
        if (cast == null) return;
        if (!caster.isAlive()) {
            end(caster);
            return;
        }
        if (caster.level().getGameTime() < cast.completesAt()) return;
        end(caster);
        cast.listener().complete(caster);
    }

    /** The caster took damage: an interruptible cast is cancelled. */
    public static void onHurt(LivingEntity caster) {
        Cast cast = CASTS.get(caster);
        if (cast == null || !cast.windup().interruptible()) return;
        end(caster);
        if (cast.windup().onInterrupt() != null) cast.windup().onInterrupt().run(new ActionContext(caster));
        cast.listener().interrupted(caster);
    }

    private static void end(LivingEntity caster) {
        CASTS.remove(caster);
        AttributeInstance speed = caster.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SLOW_ID) != null) speed.removeModifier(SLOW_ID);
    }

    private static void applySlow(LivingEntity caster, float keep) {
        if (keep >= 1f) return;
        AttributeInstance speed = caster.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.getModifier(SLOW_ID) != null) return;
        double amount = Math.max(0f, keep) - 1.0;
        //? if >=1.21 {
        speed.addTransientModifier(new AttributeModifier(SLOW_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        //?} else {
        /*speed.addTransientModifier(new AttributeModifier(SLOW_ID, "townstead:windup_slow", amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        *///?}
    }

    public static void clear() {
        CASTS.clear();
    }
}
