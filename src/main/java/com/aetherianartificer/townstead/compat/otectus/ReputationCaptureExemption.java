package com.aetherianartificer.townstead.compat.otectus;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Proxy;
import java.util.Set;

/**
 * MCA: Reputation's side of the capture amnesty. Reputation records villager assaults and killings
 * itself, so it is told through its incident exemptions that a hit inside a capture window is not a
 * deed. Reflection only; Reputation builds without the exemptions (before 0.6.1) leave it off.
 */
public final class ReputationCaptureExemption {
    private static final String API = "dev.otectus.mcareputation.api.CoreIncidentExemptions";
    private static final String KIND = "dev.otectus.mcareputation.api.CoreIncidentKind";

    private ReputationCaptureExemption() {}

    public static void register() {
        if (!ModCompat.isLoaded(OtectusBridge.REPUTATION) || !ModCompat.isLoaded("vampirism")) return;
        try {
            Class<?> api = Class.forName(API);
            if (!Integer.valueOf(1).equals(api.getMethod("capabilityVersion").invoke(null))) {
                Townstead.LOGGER.warn("[Reputation] Unknown incident exemption version; villager harm during "
                        + "a Vampirism capture still counts for reputation.");
                return;
            }
            Class<?> kind = Class.forName(KIND);
            Class<?> query = Class.forName(API + "$Query");
            Class<?> decision = Class.forName(API + "$Decision");
            Object pass = constant(decision, "PASS"), exempt = constant(decision, "EXEMPT");
            Object adapter = Proxy.newProxyInstance(query.getClassLoader(), new Class<?>[]{query},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "evaluate" -> covered(args[2]) ? exempt : pass;
                        case "toString" -> "Townstead Vampirism capture amnesty";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
            api.getMethod("register", ResourceLocation.class, Set.class, query).invoke(null,
                    ResourceLocation.tryParse("townstead:capture_amnesty"),
                    Set.of(constant(kind, "MCA_VILLAGER_ASSAULT"), constant(kind, "MCA_VILLAGER_KILL")), adapter);
        } catch (Throwable t) {
            Townstead.LOGGER.warn("[Reputation] Incident exemptions unavailable (MCA: Reputation 0.6.1 or newer "
                    + "adds them); villager harm during a Vampirism capture still counts for reputation: {}", t.toString());
        }
    }

    private static boolean covered(@Nullable Object target) {
        return target instanceof LivingEntity living && living.level() instanceof ServerLevel level
                && CaptureAmnesty.covers(level, living.blockPosition());
    }

    private static Object constant(Class<?> type, String name) {
        for (Object value : type.getEnumConstants()) {
            if (((Enum<?>) value).name().equals(name)) return value;
        }
        throw new IllegalStateException(type.getName() + " has no " + name);
    }
}
