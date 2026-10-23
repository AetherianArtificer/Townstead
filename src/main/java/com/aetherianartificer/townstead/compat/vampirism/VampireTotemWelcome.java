package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.politics.relations.FactionRelations;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

/**
 * A settlement whose Vampirism totem the vampires control welcomes vampires. When hunters take the
 * totem back, the welcome goes with it; a faction's own declaration is untouched. Reflection only.
 */
public final class VampireTotemWelcome implements FactionRelations.WelcomeSource {
    private static final Set<String> VAMPIRE = Set.of("vampire");
    private static volatile boolean resolved;
    private static volatile Method totemNearPos;
    private static volatile Method controllingFaction;

    private VampireTotemWelcome() {}

    public static void register() {
        FactionRelations.register(new VampireTotemWelcome());
    }

    @Override
    public Set<String> welcomes(MinecraftServer server, Faction faction) {
        Object vampires = VampireVillagers.vampireFaction();
        if (vampires == null || !resolve()) return Set.of();
        for (SettlementRef settlement : faction.settlements()) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
            Village village = level == null ? null : VillageManager.get(level).getOrEmpty(settlement.villageId()).orElse(null);
            if (village != null && vampires.equals(controller(level, new BlockPos(village.getCenter())))) return VAMPIRE;
        }
        return Set.of();
    }

    /** The faction controlling the Vampirism totem nearest {@code center}, or null for none or unreadable. */
    static Object controller(ServerLevel level, BlockPos center) {
        if (!resolve()) return null;
        try {
            Optional<?> totem = (Optional<?>) totemNearPos.invoke(null, level, center, true);
            return totem.isPresent() ? controllingFaction.invoke(totem.get()) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean resolve() {
        if (resolved) return totemNearPos != null;
        try {
            Class<?> helper = Class.forName("de.teamlapen.vampirism.util.TotemHelper");
            Class<?> totem = Class.forName("de.teamlapen.vampirism.blockentity.TotemBlockEntity");
            totemNearPos = helper.getMethod("getTotemNearPos", ServerLevel.class, BlockPos.class, boolean.class);
            controllingFaction = totem.getMethod("getControllingFaction");
        } catch (Throwable ignored) {
            totemNearPos = null;
        }
        resolved = true;
        return totemNearPos != null;
    }
}
