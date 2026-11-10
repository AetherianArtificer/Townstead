package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.culture.CultureAssignment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.Names;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.WeakHashMap;

/** Initial MCA naming is provisional until finalizeSpawn has assigned the root. */
public final class SpawnNaming {
    private static final Map<VillagerEntityMCA, Boolean> PENDING = new WeakHashMap<>();
    /**
     * Set while MCA turns a baby item into a child. A player chose the name and MCA assigns the
     * parents only after the spawn, so nothing about the name is settled until {@link Naming#settleBirth}.
     */
    private static final ThreadLocal<Boolean> BIRTH = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private SpawnNaming() {}

    public static void begin(VillagerEntityMCA villager) {
        // On 1.20.1 a picked name is not a custom name, so the check alone would roll over it.
        PENDING.put(villager, !villager.hasCustomName() && !inBirth());
    }

    public static boolean pending(VillagerEntityMCA villager) {
        return PENDING.containsKey(villager);
    }

    public static void beginBirth() {
        BIRTH.set(Boolean.TRUE);
    }

    public static boolean inBirth() {
        return BIRTH.get();
    }

    public static void endBirth() {
        BIRTH.remove();
    }

    public static void complete(VillagerEntityMCA villager) {
        Boolean needsName = PENDING.remove(villager);
        if (needsName == null || inBirth() || !(villager.level() instanceof ServerLevel level)) return;
        CultureAssignment.ensure(level, villager);
        if (needsName) {
            villager.setCustomName(Component.literal(
                    Names.pickCitizenName(villager.getGenetics().getGender(), villager)));
        }
        Naming.settleFromParents(villager);
        VillagerNames.publish(villager);
    }
}
