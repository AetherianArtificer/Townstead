package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.entity.VillagerEntityMCA;

import java.lang.reflect.Method;
import java.util.Locale;

public final class McaSicknessAdapter {
    private static Method sicknessSetter;
    private static boolean searched;

    private McaSicknessAdapter() {}

    public static void markSick(VillagerEntityMCA villager, boolean severe) {
        if (villager == null) return;
        resolve(villager);
        if (sicknessSetter == null) return;
        try {
            Class<?> param = sicknessSetter.getParameterTypes()[0];
            if (param == boolean.class || param == Boolean.class) {
                sicknessSetter.invoke(villager, true);
            } else if (param == int.class || param == Integer.class) {
                sicknessSetter.invoke(villager, severe ? 2 : 1);
            }
        } catch (Exception ignored) {
            // Best-effort compatibility hook.
        }
    }

    private static void resolve(VillagerEntityMCA villager) {
        if (searched) return;
        searched = true;
        for (Method method : villager.getClass().getMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!(name.contains("sick") || name.contains("ill") || name.contains("disease"))) continue;
            if (method.getParameterCount() != 1) continue;
            Class<?> param = method.getParameterTypes()[0];
            if (param == boolean.class || param == Boolean.class || param == int.class || param == Integer.class) {
                sicknessSetter = method;
                break;
            }
        }
    }
}
