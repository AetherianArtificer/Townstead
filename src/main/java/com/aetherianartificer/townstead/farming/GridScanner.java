package com.aetherianartificer.townstead.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side grid scanner for the Field Post Plot Planner.
 * Scans the world around a Field Post and produces a compact {@link GridSnapshot}.
 */
public final class GridScanner {
    private GridScanner() {}

    /**
     * Scans the area around a Field Post and builds a grid snapshot.
     * Must be called on the server thread.
     */
    public static GridSnapshot scan(ServerLevel level, BlockPos postPos, int radius) {
        int gridSize = radius * 2 + 1;
        int count = gridSize * gridSize;
        int half = gridSize / 2;
        int baseY = postPos.getY();

        byte[] flags = new byte[count];
        int[] groundBlockIds = new int[count];
        int[] cropItemIds = new int[count];
        byte[] cropAges = new byte[count];
        byte[] cropMaxAges = new byte[count];

        CropProductResolver resolver = CropProductResolver.get(level);

        for (int gz = 0; gz < gridSize; gz++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int idx = gz * gridSize + gx;
                int wx = postPos.getX() + (gx - half);
                int wz = postPos.getZ() + (gz - half);

                // Field post marker
                if (gx == half && gz == half) {
                    flags[idx] = GridSnapshot.FLAG_POST;
                    BlockState below = level.getBlockState(new BlockPos(wx, baseY - 1, wz));
                    groundBlockIds[idx] = BuiltInRegistries.BLOCK.getId(below.getBlock());
                    continue;
                }

                // Smart per-column scan: find the farm surface
                BlockPos groundPos = null;
                BlockState groundState = null;

                // Pass 1: farmland (always wins)
                for (int dy = 3; dy >= -3; dy--) {
                    BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
                    BlockState state = level.getBlockState(candidate);
                    if (state.getBlock() instanceof FarmBlock) {
                        groundPos = candidate;
                        groundState = state;
                        break;
                    }
                }

                // Pass 2: any solid ground (skip trees, crops, fluids)
                if (groundPos == null) {
                    for (int dy = 3; dy >= -3; dy--) {
                        BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
                        BlockState state = level.getBlockState(candidate);
                        if (state.isAir()) continue;
                        if (state.getBlock() instanceof CropBlock) continue;
                        if (state.getBlock() instanceof BushBlock) continue;
                        if (state.getBlock() instanceof LeavesBlock) continue;
                        if (state.is(BlockTags.LOGS)) continue;
                        if (state.is(BlockTags.FENCES)) continue;
                        if (state.is(BlockTags.WALLS)) continue;
                        if (state.is(BlockTags.SIGNS)) continue;
                        if (state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.LAVA)) continue;
                        groundPos = candidate;
                        groundState = state;
                        break;
                    }
                }

                // Pass 3: water
                if (groundPos == null) {
                    for (int dy = 3; dy >= -3; dy--) {
                        BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
                        BlockState state = level.getBlockState(candidate);
                        if (state.getFluidState().is(Fluids.WATER)) {
                            groundPos = candidate;
                            groundState = state;
                            break;
                        }
                    }
                }

                if (groundPos == null) {
                    flags[idx] = GridSnapshot.FLAG_AIR;
                    continue;
                }

                // Check one block above ground for crops/water
                BlockPos abovePos = groundPos.above();
                BlockState aboveState = level.getBlockState(abovePos);

                // If water is above ground, treat the cell as water
                if (aboveState.getFluidState().is(Fluids.WATER)) {
                    flags[idx] = GridSnapshot.FLAG_WATER;
                    groundBlockIds[idx] = BuiltInRegistries.BLOCK.getId(Blocks.WATER);
                    continue;
                }

                // Encode ground
                groundBlockIds[idx] = BuiltInRegistries.BLOCK.getId(groundState.getBlock());

                byte cellFlags = 0;
                if (groundState.getFluidState().is(Fluids.WATER)) {
                    cellFlags |= GridSnapshot.FLAG_WATER;
                } else if (groundState.getBlock() instanceof FarmBlock) {
                    cellFlags |= GridSnapshot.FLAG_FARMLAND;
                    if (groundState.getValue(FarmBlock.MOISTURE) > 0) {
                        cellFlags |= GridSnapshot.FLAG_MOIST;
                    }
                }

                // Encode crop above ground
                if (aboveState.getBlock() instanceof CropBlock crop) {
                    cellFlags |= GridSnapshot.FLAG_HAS_CROP;
                    int age = crop.getAge(aboveState);
                    int maxAge = crop.getMaxAge();
                    if (age >= maxAge) cellFlags |= GridSnapshot.FLAG_MATURE;
                    cropAges[idx] = (byte) age;
                    cropMaxAges[idx] = (byte) maxAge;
                    // Resolve crop product via loot table
                    Item product = resolver.getCropProduct(aboveState, level, abovePos);
                    if (product != null && product != Items.AIR) {
                        cropItemIds[idx] = BuiltInRegistries.ITEM.getId(product);
                    }
                } else if (aboveState.getBlock() instanceof BushBlock) {
                    cellFlags |= GridSnapshot.FLAG_HAS_CROP;
                    Item product = resolver.getCropProduct(aboveState, level, abovePos);
                    if (product != null && product != Items.AIR) {
                        cropItemIds[idx] = BuiltInRegistries.ITEM.getId(product);
                    }
                }

                flags[idx] = cellFlags;
            }
        }

        return new GridSnapshot(gridSize, flags, groundBlockIds, cropItemIds, cropAges, cropMaxAges);
    }

    /**
     * Counts all seeds available in the village: villager inventories + containers within radius.
     */
    public static Map<String, Integer> countVillageSeeds(ServerLevel level, BlockPos postPos, int radius) {
        Map<String, Integer> counts = new HashMap<>();
        int minX = postPos.getX() - radius, maxX = postPos.getX() + radius;
        int minZ = postPos.getZ() - radius, maxZ = postPos.getZ() + radius;
        int minY = postPos.getY() - 4, maxY = postPos.getY() + 4;

        // Scan villager inventories
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        for (net.minecraft.world.entity.Entity entity : level.getEntities((net.minecraft.world.entity.Entity) null, area, e -> true)) {
            if (entity instanceof net.minecraft.world.entity.npc.AbstractVillager villager) {
                net.minecraft.world.SimpleContainer inv = villager.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty() && com.aetherianartificer.townstead.block.CropDetection.isPlantableSeed(stack.getItem())) {
                        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        counts.merge(id, stack.getCount(), Integer::sum);
                    }
                }
            }
        }

        // Scan containers (chests, barrels, etc.) within the radius
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    BlockPos containerPos = new BlockPos(bx, by, bz);
                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(containerPos);
                    if (be instanceof net.minecraft.world.Container container) {
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            ItemStack stack = container.getItem(i);
                            if (!stack.isEmpty() && com.aetherianartificer.townstead.block.CropDetection.isPlantableSeed(stack.getItem())) {
                                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                                counts.merge(id, stack.getCount(), Integer::sum);
                            }
                        }
                    }
                }
            }
        }

        return counts;
    }

    /**
     * Computes status data from a snapshot.
     */
    public static int[] computeStatus(GridSnapshot snapshot) {
        int totalPlots = 0, tilledPlots = 0, hydratedPlots = 0;
        for (int i = 0; i < snapshot.cellCount(); i++) {
            byte f = snapshot.flags()[i];
            if ((f & GridSnapshot.FLAG_FARMLAND) != 0) {
                totalPlots++;
                tilledPlots++;
                if ((f & GridSnapshot.FLAG_MOIST) != 0) hydratedPlots++;
            }
        }
        int hydrationPercent = tilledPlots > 0 ? (hydratedPlots * 100 / tilledPlots) : 0;
        return new int[]{0 /* farmerCount - TODO */, totalPlots, tilledPlots, hydrationPercent};
    }
}
