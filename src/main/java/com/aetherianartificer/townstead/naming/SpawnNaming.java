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
    private SpawnNaming() {}

    public static void begin(VillagerEntityMCA villager) {
        PENDING.put(villager, !villager.hasCustomName());
    }

    public static boolean pending(VillagerEntityMCA villager) {
        return PENDING.containsKey(villager);
    }

    public static void complete(VillagerEntityMCA villager) {
        Boolean needsName = PENDING.remove(villager);
        if (needsName == null || !(villager.level() instanceof ServerLevel level)) return;
        CultureAssignment.ensure(level, villager);
        if (needsName) {
            villager.setCustomName(Component.literal(
                    Names.pickCitizenName(villager.getGenetics().getGender(), villager)));
        }
        VillagerNames.publish(villager);
    }
}
