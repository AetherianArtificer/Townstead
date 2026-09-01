package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.work.job.WorkJobDef;
import com.aetherianartificer.townstead.work.job.WorkJobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
                WorkTaskTypes.COOK.toString(), BuiltInRegistries.ITEM.getKey(stack.getItem()),
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

    /**
     * Player counterpart to data-driven villager block-interaction Jobs. Forge/NeoForge expose
     * the right-click before vanilla performs it, so a Job is credited only when its authored
     * target, state condition, held-item condition, and action precondition all agree that this
     * click is a real completion. This is what lets a Career pack make harvesting a full beehive
     * visible in the player's Career record without shipping a Java integration of its own.
     */
    public static void onDataDrivenBlockInteraction(Player player, BlockPos pos, ItemStack held) {
        if (!(player instanceof ServerPlayer sp) || sp instanceof FakePlayer
                || pos == null || held == null || held.isEmpty()) return;
        ServerLevel level = sp.serverLevel();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());

        for (WorkJobDef job : WorkJobs.forType(WorkJobDef.BLOCK_INTERACTION)) {
            WorkJobDef.BlockTarget target = job.target();
            if (target == null || !target.matches(level, pos) || !target.ready(level, pos)) continue;
            WorkJobDef.Interaction interaction = target.interactions().stream()
                    .filter(candidate -> candidate.matches(level, pos, held))
                    .findFirst().orElse(null);
            if (interaction == null) continue;

            ResourceLocation output = interaction.outputIds().stream().sorted().findFirst().orElse(blockId);
            int amount = Math.max(1, interaction.expectedCount());
            for (var def : ProfessionDefs.all().values()) {
                boolean ownsJob = def.workTasks().stream().anyMatch(task ->
                        task.type().equals(job.task()) && task.allowsBlock(blockId));
                if (!ownsJob) continue;
                CareerProgression.completeWork(sp, def.id(), Math.max(1, interaction.xp()),
                        level.getGameTime(), interaction.activityKey(job), output, "item", amount,
                        Map.of("job", job.id().toString(), "amount", Integer.toString(amount)));
                // One physical Job belongs to one Career. If two definitions accidentally claim
                // the same task and target, deterministic data order wins instead of double XP.
                return;
            }
        }
    }
}
