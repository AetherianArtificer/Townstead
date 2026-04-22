package com.aetherianartificer.townstead.compat.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
//? if >=1.21 {
import net.minecraft.world.level.storage.loot.LootTable;
//?}
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mirrors {@code IronGolemCutUpProcedure} from Butchery for the
 * villager-driven path. The mod's own procedure progresses through ten
 * hacksaw strokes tracked in a {@code golemSaw} block-entity NBT double;
 * on strokes 3 / 6 / 9 / 10 it also advances the {@code blockstate}
 * property (0-3) and drops a body-part loot table.
 *
 * <pre>
 *   stroke 1 : saw 0 -> 1
 *   stroke 2 : saw 1 -> 2
 *   stroke 3 : saw 2 -> 3, blockstate -> 1, drop butchery:blocks/iron_golem_head_drop
 *   stroke 4 : saw 3 -> 4
 *   stroke 5 : saw 4 -> 5
 *   stroke 6 : saw 5 -> 6, blockstate -> 2, drop butchery:blocks/iron_golem_arm_drop
 *   stroke 7 : saw 6 -> 7
 *   stroke 8 : saw 7 -> 8
 *   stroke 9 : saw 8 -> 9, blockstate -> 3, drop butchery:blocks/iron_golem_body_drop
 *   stroke 10: saw 9 -> 10, drop butchery:blocks/iron_golem_legs_drop, setBlock AIR
 * </pre>
 *
 * The mod's procedure accepts both {@code butchery:iron_golem} (from a
 * kill drop) and {@code butchery:repaired_iron_golem} (from its repair
 * cycle). We handle both with one path since the cut sequence is
 * identical.
 */
public final class GolemStateMachine {
    public static final String GOLEM_SAW_KEY = "golemSaw";
    public static final String BLOCKSTATE_PROPERTY = "blockstate";
    public static final int STROKE_XP = 2;

    public static final ResourceLocation IRON_GOLEM_BLOCK_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:iron_golem");
            //?} else {
            /*new ResourceLocation("butchery", "iron_golem");
            *///?}
    public static final ResourceLocation REPAIRED_IRON_GOLEM_BLOCK_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:repaired_iron_golem");
            //?} else {
            /*new ResourceLocation("butchery", "repaired_iron_golem");
            *///?}

    public record Stroke(int fromSaw, int toSaw, int newBlockstate,
                         @Nullable String lootPath, boolean terminal) {}

    public static final Stroke[] STROKES = {
            new Stroke(0, 1, -1, null, false),
            new Stroke(1, 2, -1, null, false),
            new Stroke(2, 3, 1, "butchery:blocks/iron_golem_head_drop", false),
            new Stroke(3, 4, -1, null, false),
            new Stroke(4, 5, -1, null, false),
            new Stroke(5, 6, 2, "butchery:blocks/iron_golem_arm_drop", false),
            new Stroke(6, 7, -1, null, false),
            new Stroke(7, 8, -1, null, false),
            new Stroke(8, 9, 3, "butchery:blocks/iron_golem_body_drop", false),
            new Stroke(9, 10, -1, "butchery:blocks/iron_golem_legs_drop", true),
    };

    private GolemStateMachine() {}

    public static boolean isIronGolem(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return IRON_GOLEM_BLOCK_ID.equals(id) || REPAIRED_IRON_GOLEM_BLOCK_ID.equals(id);
    }

    /** Read the {@code golemSaw} NBT; 0 if the entity or key is missing. */
    public static int readSaw(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return 0;
        CompoundTag tag = be.getPersistentData();
        return (int) tag.getDouble(GOLEM_SAW_KEY);
    }

    /**
     * Next stroke to execute given the golem's current {@code golemSaw}
     * state, or null if the golem has already been fully cut (i.e. the
     * block should be gone; any lingering state past stroke 10 is a no-op).
     */
    @Nullable
    public static Stroke nextStroke(int currentSaw) {
        for (Stroke s : STROKES) {
            if (s.fromSaw == currentSaw) return s;
        }
        return null;
    }

    /** True when there is at least one more stroke the villager can apply. */
    public static boolean isProcessable(Level level, BlockState state, BlockPos pos) {
        if (!isIronGolem(state)) return false;
        return nextStroke(readSaw(level, pos)) != null;
    }

    /**
     * Apply one stroke to the golem at {@code pos}. Advances the saw
     * counter, optionally advances the {@code blockstate} property, rolls
     * the loot table if the stroke produces a body-part drop, and removes
     * the block on the terminal stroke. Returns the items dropped this
     * stroke (possibly empty).
     */
    public static List<ItemStack> advance(ServerLevel level, BlockPos pos) {
        List<ItemStack> drops = new ArrayList<>();
        BlockState current = level.getBlockState(pos);
        if (!isIronGolem(current)) return drops;
        int currentSaw = readSaw(level, pos);
        Stroke stroke = nextStroke(currentSaw);
        if (stroke == null) return drops;

        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            CompoundTag tag = be.getPersistentData();
            tag.putDouble(GOLEM_SAW_KEY, stroke.toSaw);
            be.setChanged();
            level.sendBlockUpdated(pos, current, current, 3);
        }

        if (stroke.newBlockstate >= 0) {
            Property<?> prop = current.getBlock().getStateDefinition()
                    .getProperty(BLOCKSTATE_PROPERTY);
            if (prop instanceof IntegerProperty ip
                    && ip.getPossibleValues().contains(stroke.newBlockstate)) {
                BlockState updated = current.setValue(ip, stroke.newBlockstate);
                level.setBlock(pos, updated, 3);
                // Re-fetch the BE; setBlock may have replaced the entity.
                BlockEntity updatedBe = level.getBlockEntity(pos);
                if (updatedBe != null) {
                    updatedBe.getPersistentData().putDouble(GOLEM_SAW_KEY, stroke.toSaw);
                    updatedBe.setChanged();
                }
            }
        }

        if (stroke.lootPath != null) {
            rollLoot(level, stroke.lootPath, drops);
        }

        if (stroke.terminal) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        return Collections.unmodifiableList(drops);
    }

    private static void rollLoot(ServerLevel level, String lootTableId, List<ItemStack> out) {
        MinecraftServer server = level.getServer();
        if (server == null) return;
        //? if >=1.21 {
        ResourceKey<LootTable> key = ResourceKey.create(
                Registries.LOOT_TABLE, ResourceLocation.parse(lootTableId));
        LootTable table = server.reloadableRegistries().getLootTable(key);
        if (table == null) return;
        out.addAll(table.getRandomItems(
                new LootParams.Builder(level).create(LootContextParamSets.EMPTY)));
        //?} else {
        /*net.minecraft.world.level.storage.loot.LootTable table = server.getLootData()
                .getLootTable(new ResourceLocation(lootTableId));
        if (table == null || table == net.minecraft.world.level.storage.loot.LootTable.EMPTY) return;
        out.addAll(table.getRandomItems(
                new LootParams.Builder(level).create(LootContextParamSets.EMPTY)));
        *///?}
    }
}
