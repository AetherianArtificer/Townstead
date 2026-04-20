package com.aetherianartificer.townstead.compat.travelerstitles;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.chat.Component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * Client-side reflection into Traveler's Titles. TT exposes no public API; we reach
 * into its internal biome-title renderer to piggyback on its title animation.
 *
 * Resolution runs once on first call; failure is logged once and thereafter silently no-ops.
 */
public final class TravelersTitlesBridge {
    private static volatile boolean resolved;
    private static volatile boolean available;
    private static volatile MethodHandle displayTitleHandle;
    private static volatile Object biomeRenderer;

    private TravelersTitlesBridge() {}

    public static void displayVillageTitle(String villageName, int population, String subtitleKey) {
        if (!ensureResolved()) return;
        Component title = Component.literal(villageName);
        Component subtitle = (subtitleKey != null && !subtitleKey.isEmpty())
                ? Component.translatable(subtitleKey)
                : Component.translatable("townstead.village_title.population", population);
        try {
            displayTitleHandle.invoke(biomeRenderer, title, subtitle);
        } catch (Throwable t) {
            Townstead.LOGGER.debug("Travelers-Titles displayTitle invocation failed", t);
        }
    }

    private static boolean ensureResolved() {
        if (resolved) return available;
        synchronized (TravelersTitlesBridge.class) {
            if (resolved) return available;
            try {
                Class<?> commonClass = Class.forName("com.yungnickyoung.minecraft.travelerstitles.TravelersTitlesCommon");
                Field managerField = commonClass.getField("titleManager");
                Object manager = managerField.get(null);
                if (manager == null) {
                    Townstead.LOGGER.info("Travelers-Titles titleManager is null; title integration disabled");
                    resolved = true;
                    return false;
                }

                Class<?> rendererClass = Class.forName("com.yungnickyoung.minecraft.travelerstitles.render.TitleRenderer");
                Field biomeField = manager.getClass().getField("biomeTitleRenderer");
                Object renderer = biomeField.get(manager);
                if (renderer == null) {
                    Townstead.LOGGER.info("Travelers-Titles biomeTitleRenderer is null; title integration disabled");
                    resolved = true;
                    return false;
                }

                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                MethodHandle mh = lookup.findVirtual(
                        rendererClass,
                        "displayTitle",
                        MethodType.methodType(void.class, Component.class, Component.class)
                );

                biomeRenderer = renderer;
                displayTitleHandle = mh;
                available = true;
                resolved = true;
                return true;
            } catch (Throwable t) {
                Townstead.LOGGER.info("Travelers-Titles bridge unavailable: {}", t.toString());
                resolved = true;
                available = false;
                return false;
            }
        }
    }
}
