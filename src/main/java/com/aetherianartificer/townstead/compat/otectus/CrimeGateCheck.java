package com.aetherianartificer.townstead.compat.otectus;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * The MCA: Crime method the capture amnesty hooks, named once for the mixin and for the startup
 * check. A mixin that no longer finds its method fails silently, so the check looks the method up
 * by the same descriptor, then for Mixin's mark of our handler on Crime's class, and warns when
 * either is missing.
 */
public final class CrimeGateCheck {
    public static final String GATE = "dev.otectus.mcacrime.detect.CrimeGate";
    public static final String OFFENDER =
            "resolveOffender(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;"
                    + "Lnet/minecraft/server/level/ServerLevel;Z)Ljava/util/Optional;";
    public static final String NON_DAMAGE_OFFENDER =
            "resolveNonDamageOffender(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/server/level/ServerLevel;)Ljava/util/Optional;";

    static final String MIXIN = "com.aetherianartificer.townstead.mixin.compat.mcacrime.CrimeGateCaptureAmnestyMixin";
    static final String MERGED = "org.spongepowered.asm.mixin.transformer.meta.MixinMerged";

    private CrimeGateCheck() {}

    public static void verify() {
        if (!ModCompat.isLoaded(OtectusBridge.CRIME) || !ModCompat.isLoaded("vampirism")) return;
        try {
            Class<?> gate = Class.forName(GATE, false, CrimeGateCheck.class.getClassLoader());
            if (!hasStatic(gate, OFFENDER)) {
                Townstead.LOGGER.warn("[Crime] {} no longer has {}. The Vampirism capture amnesty is off: "
                        + "players are charged for harming villagers during a capture.", GATE, OFFENDER);
            } else if (!merged(gate, MERGED, MIXIN)) {
                Townstead.LOGGER.warn("[Crime] The capture amnesty mixin did not attach to {}. The Vampirism "
                        + "capture amnesty is off: players are charged for harming villagers during a capture.", GATE);
            } else {
                Townstead.LOGGER.info("[Crime] Vampirism capture amnesty active{}", hasStatic(gate, NON_DAMAGE_OFFENDER)
                        ? "" : " (damaging blows only; this Crime has no " + NON_DAMAGE_OFFENDER + ")");
            }
        } catch (Throwable t) {
            Townstead.LOGGER.warn("[Crime] Could not inspect {}. The Vampirism capture amnesty may be off: {}",
                    GATE, t.toString());
        }
    }

    /** Whether {@code gate} declares a public static method with this name and JVM descriptor. */
    static boolean hasStatic(Class<?> gate, String nameAndDescriptor) {
        for (Method method : gate.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    && nameAndDescriptor.equals(method.getName() + descriptor(method))) return true;
        }
        return false;
    }

    /**
     * Whether a method in {@code gate} carries Mixin's merge mark naming {@code mixin}. The mark is
     * read by its type's name so this class never links against Mixin's internals.
     */
    static boolean merged(Class<?> gate, String markType, String mixin) {
        for (Method method : gate.getDeclaredMethods()) {
            for (Annotation mark : method.getDeclaredAnnotations()) {
                if (!mark.annotationType().getName().equals(markType)) continue;
                try {
                    if (mixin.equals(mark.annotationType().getMethod("mixin").invoke(mark))) return true;
                } catch (ReflectiveOperationException ignored) {
                    // A mark without a mixin name says nothing about ours.
                }
            }
        }
        return false;
    }

    private static String descriptor(Method method) {
        StringBuilder out = new StringBuilder("(");
        for (Class<?> type : method.getParameterTypes()) out.append(descriptor(type));
        return out.append(')').append(descriptor(method.getReturnType())).toString();
    }

    private static String descriptor(Class<?> type) {
        if (type == boolean.class) return "Z";
        if (type == void.class) return "V";
        if (type.isPrimitive()) throw new IllegalArgumentException(type.getName());
        return "L" + type.getName().replace('.', '/') + ";";
    }
}
