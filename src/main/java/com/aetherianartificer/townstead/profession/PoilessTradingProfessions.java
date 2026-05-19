package com.aetherianartificer.townstead.profession;

import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

/**
 * Registry of Townstead-managed villager professions that:
 *   (a) have no job-site POI (registered with {@code PoiType.NONE}), and
 *   (b) have trade offers registered via VillagerTradesEvent.
 *
 * <p>Vanilla {@code Villager.restock()} fires from {@code workAtPoi()}, which
 * only runs when the villager is at their workstation POI. POI-less
 * professions therefore never restock through the vanilla path; Townstead
 * drives restocking for them explicitly via {@link
 * com.aetherianartificer.townstead.tick.PoilessTradeRestockTicker}.
 *
 * <p>Suppliers are resolved lazily so this can be populated during
 * {@code FMLCommonSetupEvent} before profession DeferredRegisters fire.
 */
public final class PoilessTradingProfessions {
    private static final Set<Supplier<VillagerProfession>> SUPPLIERS = new CopyOnWriteArraySet<>();

    private PoilessTradingProfessions() {}

    public static void register(Supplier<VillagerProfession> supplier) {
        SUPPLIERS.add(supplier);
    }

    public static boolean contains(VillagerProfession profession) {
        if (profession == null) return false;
        for (Supplier<VillagerProfession> s : SUPPLIERS) {
            if (s.get() == profession) return true;
        }
        return false;
    }
}
