package com.aetherianartificer.townstead.compat.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import java.util.List;

/**
 * Mirrors the drained-carcass state transitions defined in Butchery's
 * per-species {@code XCutupProcedure} classes, without going through
 * {@code PlayerInteractEvent}. State values come from Butchery's internal
 * blockstate property:
 * <pre>
 *   0 (head)     → 6  drop {species}_head_drop
 *   6 (skin)     → 7  drop {species}_skin_drop
 *   7 (cut 1)    → 8  drop {species}_cut_1_drop (+ organs_drop_1)
 *   8 (cut 2)    → 9  drop {species}_cut_2_drop (+ organs_drop_2)
 *   9 (cut 3)    → removed; drop {species}_cut_3_drop (+ organs_drop_3)
 * </pre>
 * See {@code docs/design/butchery_integration.md} section 14 for the
 * version-pinning and fallback notes that justify this direct replication.
 */
public final class CarcassStateMachine {
    public static final TagKey<net.minecraft.world.level.block.Block> CARCASS_TAG =
            //? if >=1.21 {
            BlockTags.create(ResourceLocation.parse("butchery:carcass"));
            //?} else {
            /*BlockTags.create(new ResourceLocation("butchery", "carcass"));
            *///?}

    public static final TagKey<net.minecraft.world.level.block.Block> DRAINED_CARCASS_TAG =
            //? if >=1.21 {
            BlockTags.create(ResourceLocation.parse("butchery:drained_carcass"));
            //?} else {
            /*BlockTags.create(new ResourceLocation("butchery", "drained_carcass"));
            *///?}

    public static final String BLOCKSTATE_PROPERTY = "blockstate";
    public static final int BLEED_XP = 1;
    private static final int BASIN_SEARCH_RADIUS = 2;

    private static final ResourceLocation BASIN_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:basin");
            //?} else {
            /*new ResourceLocation("butchery", "basin");
            *///?}

    /** A single stage transition. */
    public enum Stage {
        HEAD(0, 6, "head_drop", null, 1),
        SKIN(6, 7, "skin_drop", null, 2),
        CUT_1(7, 8, "cut_1_drop", "organs_drop_1", 3),
        CUT_2(8, 9, "cut_2_drop", "organs_drop_2", 3),
        CUT_3(9, -1, "cut_3_drop", "organs_drop_3", 3);

        public final int fromState;
        public final int toState;
        public final String lootSuffix;
        @Nullable public final String organsSuffix;
        public final int xpGrant;

        Stage(int fromState, int toState, String lootSuffix, @Nullable String organsSuffix, int xpGrant) {
            this.fromState = fromState;
            this.toState = toState;
            this.lootSuffix = lootSuffix;
            this.organsSuffix = organsSuffix;
            this.xpGrant = xpGrant;
        }

        @Nullable
        public static Stage forCurrentState(int current) {
            for (Stage s : values()) if (s.fromState == current) return s;
            return null;
        }
    }

    private CarcassStateMachine() {}

    public static boolean isDrainedCarcass(BlockState state) {
        return state.is(DRAINED_CARCASS_TAG);
    }

    /** True for carcasses that have not yet been bled. */
    public static boolean isFreshCarcass(BlockState state) {
        return state.is(CARCASS_TAG) && !state.is(DRAINED_CARCASS_TAG);
    }

    /** True for carcass blocks the workflow driver should consider (fresh with basin, or drained with a processable state). */
    public static boolean isProcessable(Level level, BlockState state, BlockPos pos) {
        if (isDrainedCarcass(state)) {
            return Stage.forCurrentState(currentState(state)) != null;
        }
        return isFreshCarcass(state) && hasBasinNearby(level, pos);
    }

    public static boolean hasBasinNearby(Level level, BlockPos carcass) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -BASIN_SEARCH_RADIUS; dy <= BASIN_SEARCH_RADIUS; dy++) {
            for (int dx = -BASIN_SEARCH_RADIUS; dx <= BASIN_SEARCH_RADIUS; dx++) {
                for (int dz = -BASIN_SEARCH_RADIUS; dz <= BASIN_SEARCH_RADIUS; dz++) {
                    cursor.set(carcass.getX() + dx, carcass.getY() + dy, carcass.getZ() + dz);
                    BlockState s = level.getBlockState(cursor);
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
                    if (BASIN_ID.equals(id)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Swap a fresh carcass with its drained twin at state 0. Returns true if
     * the replacement succeeded. Pure block swap; no loot, no blood fluid —
     * the villager shortcut skips the fluid-fill cosmetics.
     */
    public static boolean bleed(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (!isFreshCarcass(current)) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(current.getBlock());
        if (id == null || !"butchery".equals(id.getNamespace())) return false;
        String path = id.getPath();
        if (!path.endsWith("_carcass")) return false;
        String drainedPath = "drained_" + path;
        //? if >=1.21 {
        ResourceLocation drainedId = ResourceLocation.fromNamespaceAndPath("butchery", drainedPath);
        //?} else {
        /*ResourceLocation drainedId = new ResourceLocation("butchery", drainedPath);
        *///?}
        if (!BuiltInRegistries.BLOCK.containsKey(drainedId)) return false;
        Block drained = BuiltInRegistries.BLOCK.get(drainedId);
        if (drained == null) return false;
        BlockState target = drained.defaultBlockState();
        Property<?> prop = target.getBlock().getStateDefinition().getProperty(BLOCKSTATE_PROPERTY);
        if (prop instanceof IntegerProperty ip && ip.getPossibleValues().contains(0)) {
            target = target.setValue(ip, 0);
        }
        level.setBlock(pos, target, 3);
        return true;
    }

    public static int currentState(BlockState state) {
        Property<?> prop = state.getBlock().getStateDefinition().getProperty(BLOCKSTATE_PROPERTY);
        if (prop instanceof IntegerProperty ip) {
            try {
                return state.getValue(ip);
            } catch (Exception ignored) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Extract the species key from a drained carcass block id like
     * {@code butchery:drained_cow_carcass} → {@code cow}. Returns null if the
     * block id does not match the expected pattern.
     */
    @Nullable
    public static String speciesFor(BlockState state) {
        Block block = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return null;
        if (!"butchery".equals(id.getNamespace())) return null;
        String path = id.getPath();
        final String prefix = "drained_";
        final String suffix = "_carcass";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) return null;
        String species = path.substring(prefix.length(), path.length() - suffix.length());
        return species.isEmpty() ? null : species;
    }

    /**
     * Advance one stage: mutate the blockstate (or remove on terminal) and
     * collect all loot into a returned stack list. Returns an empty list if
     * the block is not in a processable state.
     */
    public static List<ItemStack> advance(ServerLevel level, BlockPos pos) {
        List<ItemStack> collected = new ArrayList<>();
        BlockState current = level.getBlockState(pos);
        if (!isDrainedCarcass(current)) return collected;
        int stateValue = currentState(current);
        Stage stage = Stage.forCurrentState(stateValue);
        if (stage == null) return collected;
        String species = speciesFor(current);
        if (species == null) return collected;

        // Drop loot tables first so the final-state removal still lands drops
        // even if we swap the block after.
        rollLoot(level, "butchery:blocks/" + species + "_" + stage.lootSuffix, collected);
        if (stage.organsSuffix != null) {
            rollLoot(level, "butchery:blocks/" + stage.organsSuffix, collected);
        }

        if (stage.toState < 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        } else {
            Property<?> prop = current.getBlock().getStateDefinition().getProperty(BLOCKSTATE_PROPERTY);
            if (prop instanceof IntegerProperty ip && ip.getPossibleValues().contains(stage.toState)) {
                level.setBlock(pos, current.setValue(ip, stage.toState), 3);
            }
        }
        return collected;
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
