package com.aetherianartificer.townstead.compat.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors the hanging-carcass workflow defined in Butchery's
 * per-species {@code HangingXCutupProcedure} classes, without going through
 * {@code PlayerInteractEvent}. Butchery's hook-placement procedure puts the
 * fresh carcass at blockstate 1 and the drained carcass lands at 1 after the
 * 45-second bleed; cut-up clicks then walk the drained blockstate through
 * 1 → 2 → 3 → 4 → 5 → AIR, each transition dropping a loot table.
 *
 * <pre>
 *   fresh(1)    → drained(1)  after 900-tick drain (blood grate below)
 *   drained 1 (head)  → 2     drop {species}_head_drop
 *   drained 2 (skin)  → 3     drop {species}_skin_drop
 *   drained 3 (cut 1) → 4     drop {species}_cut_1_drop (+ organs_drop_1)
 *   drained 4 (cut 2) → 5     drop {species}_cut_2_drop (+ organs_drop_2)
 *   drained 5 (cut 3) → AIR   drop {species}_cut_3_drop (+ organs_drop_3)
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
    /** The placement/bleed block-state value the mod uses for a hung carcass.
     *  Fresh carcasses are placed at 1 by PlacecowcarcassProcedure, and the
     *  drained twin lands at 1 after the bleed timer. */
    public static final int HUNG_BLOCKSTATE = 1;
    /** How long the mod's bleed timer runs before the fresh→drained swap. */
    public static final int DRAIN_DURATION_TICKS = 900;
    /** How far below a hanging carcass the mod scans for a blood grate. */
    private static final int GRATE_SEARCH_DEPTH = 10;
    /** Block-entity NBT key holding the game-time at which the carcass
     *  finishes draining. 0 or missing means drain hasn't been initiated. */
    public static final String DRAIN_READY_TICK_KEY = "townstead_drainReadyTick";
    /** Mod-native NBT keys — set them alongside our timer so other tooling
     *  (visuals, advancements, other mod interactions) sees a consistent
     *  state. */
    public static final String IS_BLEEDING_KEY = "isBleeding";
    public static final String IS_DRAINED_KEY = "isDrained";

    private static final ResourceLocation BLOOD_GRATE_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:blood_grate");
            //?} else {
            /*new ResourceLocation("butchery", "blood_grate");
            *///?}

    /** A single stage transition. */
    public enum Stage {
        HEAD(1, 2, "head_drop", null, 1),
        SKIN(2, 3, "skin_drop", null, 2),
        CUT_1(3, 4, "cut_1_drop", "organs_drop_1", 3),
        CUT_2(4, 5, "cut_2_drop", "organs_drop_2", 3),
        CUT_3(5, -1, "cut_3_drop", "organs_drop_3", 3);

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

    /**
     * True for carcass blocks the workflow driver should actively work on:
     * fresh carcasses need their drain initiated OR are ready to finish the
     * 45-second bleed, and drained carcasses at a known cutting state are
     * always actionable.
     *
     * <p>A fresh carcass whose drain timer is running but not yet complete
     * is intentionally <strong>not</strong> processable — it needs no butcher
     * attention while the blood drips, matching the player flow where one
     * click initiates a passive 900-tick timer.
     */
    public static boolean isProcessable(Level level, BlockState state, BlockPos pos) {
        if (isDrainedCarcass(state)) {
            return Stage.forCurrentState(currentState(state)) != null;
        }
        if (!isFreshCarcass(state)) return false;
        if (!hasBloodGrateBelow(level, pos)) return false;
        if (!(level instanceof ServerLevel sl)) return true;
        long readyTick = readDrainReadyTick(level, pos);
        if (readyTick != 0L) {
            // Our timer is running; processable once it matures.
            return sl.getGameTime() >= readyTick;
        }
        // No Townstead timer. If the mod's own bleed procedure (player
        // right-click, another butcher, a different mod's trigger) already
        // set isBleeding=true, its 900-tick queued task is in flight — let
        // it finish rather than firing a parallel second drain.
        return !isBleedingInProgress(level, pos);
    }

    /**
     * True if the mod's {@code isBleeding} flag is set on the carcass's
     * block entity. That flag is written by {@code XcarcassbleedingProcedure}
     * at the start of its 900-tick drain and cleared when the drain swaps
     * the block to its drained twin.
     */
    public static boolean isBleedingInProgress(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        CompoundTag tag = be.getPersistentData();
        return tag.getBoolean(IS_BLEEDING_KEY);
    }

    /**
     * Mirrors {@code FillbloodgrateProcedure}: Butchery scans straight down
     * from the carcass (Y-1 through Y-{@value #GRATE_SEARCH_DEPTH}) looking
     * for a blood grate to absorb blood during the drain. No blood grate in
     * that column, no drain — so the butcher has nothing to start.
     */
    public static boolean hasBloodGrateBelow(Level level, BlockPos carcass) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= GRATE_SEARCH_DEPTH; dy++) {
            cursor.set(carcass.getX(), carcass.getY() - dy, carcass.getZ());
            BlockState s = level.getBlockState(cursor);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
            if (BLOOD_GRATE_ID.equals(id)) return true;
        }
        return false;
    }

    /**
     * Stamp the block entity so the carcass will finish draining {@value
     * #DRAIN_DURATION_TICKS} ticks from now. Also sets the mod's native
     * {@code isBleeding} flag so any mod visuals that key off it are
     * consistent. Returns true on success.
     */
    public static boolean initiateBleed(ServerLevel level, BlockPos pos, long gameTime) {
        BlockState current = level.getBlockState(pos);
        if (!isFreshCarcass(current)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        CompoundTag tag = be.getPersistentData();
        tag.putBoolean(IS_BLEEDING_KEY, true);
        tag.putLong(DRAIN_READY_TICK_KEY, gameTime + DRAIN_DURATION_TICKS);
        be.setChanged();
        level.sendBlockUpdated(pos, current, current, 3);
        return true;
    }

    /**
     * Butchery exposes this as a loader-specific config value and Townstead's
     * compat remains optional, so read it reflectively instead of taking a
     * compile-time dependency on Butchery classes.
     */
    public static boolean instantBleedEnabled() {
        if (!ButcheryCompat.isLoaded()) return false;
        try {
            Class<?> config = Class.forName("net.mcreator.butchery.configuration.ButcheryconfigConfiguration");
            Field field = config.getField("INSTANT_BLEED");
            Object configValue = field.get(null);
            if (configValue == null) return false;
            Method get = configValue.getClass().getMethod("get");
            Object value = get.invoke(configValue);
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** Reads the drain-ready tick stored on the block entity, or 0 if unset. */
    public static long readDrainReadyTick(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return 0L;
        CompoundTag tag = be.getPersistentData();
        if (!tag.contains(DRAIN_READY_TICK_KEY)) return 0L;
        return tag.getLong(DRAIN_READY_TICK_KEY);
    }

    /**
     * Finishes the bleed cycle: swap the fresh carcass for its drained twin
     * at {@link #HUNG_BLOCKSTATE} and flip the mod's {@code isBleeding} /
     * {@code isDrained} flags to match the state the real mod writes after
     * its 900-tick timer fires.
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
        if (prop instanceof IntegerProperty ip && ip.getPossibleValues().contains(HUNG_BLOCKSTATE)) {
            target = target.setValue(ip, HUNG_BLOCKSTATE);
        }
        // Preserve facing so the drained twin hangs the same direction.
        Property<?> facing = current.getBlock().getStateDefinition().getProperty("facing");
        Property<?> newFacing = target.getBlock().getStateDefinition().getProperty("facing");
        if (facing != null && newFacing != null) {
            try {
                target = copyProperty(current, target, facing, newFacing);
            } catch (Exception ignored) {}
        }
        level.setBlock(pos, target, 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            CompoundTag tag = be.getPersistentData();
            tag.putBoolean(IS_BLEEDING_KEY, false);
            tag.putBoolean(IS_DRAINED_KEY, true);
            tag.remove(DRAIN_READY_TICK_KEY);
            be.setChanged();
            level.sendBlockUpdated(pos, target, target, 3);
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProperty(BlockState from, BlockState to, Property fromProp, Property toProp) {
        Object value = from.getValue(fromProp);
        if (toProp.getPossibleValues().contains(value)) {
            return to.setValue(toProp, (Comparable) value);
        }
        return to;
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
