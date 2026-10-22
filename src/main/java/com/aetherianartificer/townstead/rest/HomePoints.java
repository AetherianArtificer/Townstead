package com.aetherianartificer.townstead.rest;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Makes home rest spots count as homes to the game and to MCA: their block states join vanilla's
 * home point of interest, which is what MCA counts for a village's beds and claims as a home.
 * Refreshed whenever tags load, before chunks do; a chunk that loads with such a block not yet
 * registered (placed before the spot existed) registers it then.
 */
public final class HomePoints {
    private static final Set<BlockState> ADDED = new HashSet<>();
    private static volatile boolean dirty = true;
    private static Field typeByState;

    private HomePoints() {}

    static void markDirty() {
        dirty = true;
    }

    /** From the tags-updated event on the server, when tags and rest spots are both current. */
    public static synchronized void refresh() {
        dirty = false;
        Map<BlockState, Holder<PoiType>> table = table();
        if (table == null) return;
        Holder<PoiType> home = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(PoiTypes.HOME);
        for (BlockState state : ADDED) table.remove(state, home);
        ADDED.clear();
        if (RestSpots.all().isEmpty()) return;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!RestSpots.isHomeBlock(block)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!RestSpots.isHome(state) || table.containsKey(state)) continue;
                table.put(state, home);
                ADDED.add(state);
            }
        }
        if (!ADDED.isEmpty()) Townstead.LOGGER.info("Rest spots: {} block states count as homes", ADDED.size());
    }

    public static boolean dirty() {
        return dirty;
    }

    /** A chunk loaded: registers any home rest spot in it the point-of-interest data does not know yet. */
    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        Set<BlockState> added;
        synchronized (HomePoints.class) {
            if (ADDED.isEmpty()) return;
            added = Set.copyOf(ADDED);
        }
        Holder<PoiType> home = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(PoiTypes.HOME);
        PoiManager poi = level.getPoiManager();
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(added::contains)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                if (!added.contains(section.getBlockState(x, y, z))) continue;
                BlockPos pos = new BlockPos(minX + x, baseY + y, minZ + z);
                if (!poi.existsAtPosition(PoiTypes.HOME, pos)) poi.add(pos, home);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockState, Holder<PoiType>> table() {
        try {
            if (typeByState == null) {
                Field found = null;
                for (String name : new String[]{"TYPE_BY_STATE", "f_218070_"}) {
                    try {
                        found = PoiTypes.class.getDeclaredField(name);
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                if (found == null) throw new NoSuchFieldException("TYPE_BY_STATE");
                found.setAccessible(true);
                typeByState = found;
            }
            return (Map<BlockState, Holder<PoiType>>) typeByState.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Townstead.LOGGER.warn("Rest spots cannot count as homes: {}", e.toString());
            return null;
        }
    }
}
