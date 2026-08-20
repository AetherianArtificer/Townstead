package com.aetherianartificer.townstead.profession.career;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayer;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayer;
*///?}

import java.util.Map;
import java.util.UUID;

/**
 * Player-side cooking attribution. The villager work engines route their own completions; these
 * hooks give players the same credit when a cooking station produces finished output. Synchronous
 * stations can report the player directly; unattended stations can remember their initiating
 * player on the block entity and resolve that player only when output is actually completed. All
 * paths funnel through {@link CareerProgression#completeWork}, so XP, chronicle taps, acquisition
 * sweeps, and level-up feedback behave identically to villager work.
 */
public final class PlayerWorkHooks {
    private static final String COOK_UUID = "TownsteadCookingPlayer";

    private PlayerWorkHooks() {}

    /** Record who initiated work at an unattended cooking station. No work is awarded yet. */
    public static void rememberCookingPlayer(BlockEntity station, Player player) {
        if (!(player instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        station.getPersistentData().putUUID(COOK_UUID, sp.getUUID());
        station.setChanged();
    }

    /** Cancel pending attribution when the input leaves before producing cooked output. */
    public static void forgetCookingPlayer(BlockEntity station) {
        CompoundTag data = station.getPersistentData();
        if (!data.contains(COOK_UUID)) return;
        data.remove(COOK_UUID);
        station.setChanged();
    }

    /**
     * Award completed output to the player who initiated this unattended station, then consume the
     * attribution. Adapters call this only after their recipe engine has committed the output.
     */
    public static void onRememberedCookingCompleted(BlockEntity station, ItemStack stack,
                                                     int completed, String stationKind) {
        CompoundTag data = station.getPersistentData();
        Level level = station.getLevel();
        if (completed > 0 && data.hasUUID(COOK_UUID) && level instanceof ServerLevel serverLevel) {
            UUID cookUuid = data.getUUID(COOK_UUID);
            ServerPlayer cook = serverLevel.getServer().getPlayerList().getPlayer(cookUuid);
            if (cook != null) onCookingCompleted(cook, stack, completed, stationKind);
        }
        if (completed > 0) forgetCookingPlayer(station);
    }

    /** Common completion contract for cooking systems which already know the responsible player. */
    public static void onCookingCompleted(Player player, ItemStack stack, int completed,
                                          String stationKind) {
        if (!(player instanceof ServerPlayer sp) || sp instanceof FakePlayer
                || stack.isEmpty() || completed <= 0) return;
        CareerProgression.completeWork(sp, Careers.COOK, completed, sp.serverLevel().getGameTime(),
                "townstead:cooked", BuiltInRegistries.ITEM.getKey(stack.getItem()),
                "dish", completed,
                Map.of("station", stationKind, "amount", Integer.toString(completed)));
    }

    /** Furnace family: only food results count as cooking. */
    public static void onSmelted(Player player, ItemStack stack) {
        boolean food;
        //? if >=1.21 {
        food = stack.has(net.minecraft.core.component.DataComponents.FOOD);
        //?} else {
        /*food = stack.getItem().isEdible();
        *///?}
        if (food) onCookingCompleted(player, stack, stack.getCount(), "furnace");
    }
}
