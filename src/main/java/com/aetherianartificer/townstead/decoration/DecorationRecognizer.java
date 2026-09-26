package com.aetherianartificer.townstead.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recognises sets as blocks land and forgets them as blocks go. Placement: every definition the
 * block could belong to is tried around every anchor within reach. Removal: every recognised set
 * the block belonged to is re-checked with that block treated as air. A completed set sparkles,
 * chimes, and names itself on the builder's action bar, the way a builder game confirms a room.
 */
public final class DecorationRecognizer {
    private DecorationRecognizer() {}

    /** Import naturally generated decorations when the player refreshes a village. */
    public static int reconcileVillage(ServerLevel level, net.conczin.mca.server.world.data.Village village) {
        if (level == null || village == null || Decorations.isEmpty()) return 0;
        BlockPos center = new BlockPos(village.getCenter().getX(), village.getCenter().getY(),
                village.getCenter().getZ());
        var box = village.getBox();
        int villageReach = Math.max(Math.max(center.getX() - box.minX(), box.maxX() - center.getX()),
                Math.max(center.getZ() - box.minZ(), box.maxZ() - center.getZ()));
        int radius = Math.min(160, Math.max(48, villageReach + 24));
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;
        DecorationSavedData data = DecorationSavedData.get(level);
        Set<Long> live = new HashSet<>();
        int added = 0;

        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                var chunk = level.getChunk(chunkX, chunkZ);
                var sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    var section = sections[index];
                    if (section == null || section.hasOnlyAir()) continue;
                    int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
                    for (int x = Math.max(minX, chunkX << 4); x <= Math.min(maxX, (chunkX << 4) + 15); x++) {
                        for (int z = Math.max(minZ, chunkZ << 4); z <= Math.min(maxZ, (chunkZ << 4) + 15); z++) {
                            for (int y = 0; y < 16; y++) {
                                BlockState state = section.getBlockState(x & 15, y, z & 15);
                                if (state.isAir()) continue;
                                BlockPos anchor = new BlockPos(x, baseY + y, z);
                                for (DecorationDefinition definition : Decorations.all()) {
                                    if (!definition.isAnchor(state)) continue;
                                    DecorationInstance instance = evaluate(level, definition, anchor, null);
                                    if (instance == null) continue;
                                    if (duplicatesNearby(data, definition, instance)) continue;
                                    live.add(anchor.asLong());
                                    DecorationInstance old = data.at(anchor);
                                    if (old == null || !old.equals(instance)) {
                                        data.put(instance);
                                        if (old == null) added++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (DecorationInstance instance : data.within(center, radius * 2)) {
            BlockPos anchor = instance.anchor();
            if (anchor.getX() < minX || anchor.getX() > maxX || anchor.getZ() < minZ || anchor.getZ() > maxZ) continue;
            if (level.isLoaded(anchor) && !live.contains(anchor.asLong())) data.remove(anchor);
        }
        return added;
    }

    public static void onPlaced(ServerLevel level, BlockPos pos, BlockState placed, @Nullable Entity by) {
        if (Decorations.isEmpty()) return;
        Decorations.warnOverlapsOnce();
        List<DecorationDefinition> candidates = new ArrayList<>(Decorations.touching(placed));
        if (candidates.isEmpty()) return;
        // Bigger sets first, so they take a shared anchor before the sets they include.
        candidates.sort(java.util.Comparator.comparingInt(definition -> -definition.supersedes().size()));
        DecorationSavedData data = DecorationSavedData.get(level);
        boolean changed = false;
        for (DecorationDefinition definition : candidates) {
            for (BlockPos anchor : anchorsNear(level, definition, pos, placed)) {
                DecorationInstance existing = data.at(anchor);
                if (existing != null && !definition.supersedes(existing.decorationId())) continue;
                DecorationInstance instance = evaluate(level, definition, anchor, null);
                if (instance == null) continue;
                if (duplicatesNearby(data, definition, instance)) continue;
                if (existing != null) data.remove(anchor);
                data.put(instance);
                releaseMembers(level, data, instance, by);
                changed = true;
                celebrate(level, definition, instance, by);
            }
        }
        if (changed) updateVillages(level, pos, by);
    }

    /**
     * An anchor belongs to one set. A set that counted the new anchor as one of its members
     * re-checks without it and is dismantled if it falls short.
     */
    private static void releaseMembers(ServerLevel level, DecorationSavedData data, DecorationInstance claimed,
                                       @Nullable Entity by) {
        for (DecorationInstance other : data.within(claimed.anchor(), Decorations.maxRadius())) {
            if (other.anchor().equals(claimed.anchor()) || !other.members().contains(claimed.anchor())) continue;
            DecorationDefinition definition = Decorations.definition(other.decorationId());
            DecorationInstance rechecked = definition == null ? null : evaluate(level, definition, other.anchor(), null);
            if (rechecked != null) {
                data.put(rechecked);
                continue;
            }
            data.remove(other.anchor());
            if (by instanceof ServerPlayer player && definition != null) {
                player.displayClientMessage(Component.translatable("townstead.decoration.dismantled",
                        Component.translatable(definition.translationKey())), true);
            }
        }
    }

    /** Multi-anchor assemblies (a four-lantern post or a haystack) are one set, not four. */
    private static boolean duplicatesNearby(DecorationSavedData data, DecorationDefinition definition,
                                            DecorationInstance candidate) {
        Set<Long> members = new HashSet<>();
        candidate.members().forEach(pos -> members.add(pos.asLong()));
        for (DecorationInstance existing : data.within(candidate.anchor(), definition.radius() * 2)) {
            if (!existing.decorationId().equals(candidate.decorationId())) continue;
            if (existing.anchor().equals(candidate.anchor())) return false;
            if (members.contains(existing.anchor().asLong())
                    || existing.members().stream().anyMatch(pos -> members.contains(pos.asLong()))) {
                return true;
            }
        }
        return false;
    }

    public static void onRemoved(ServerLevel level, BlockPos pos, BlockState removed, @Nullable Entity by) {
        if (Decorations.isEmpty() || Decorations.touching(removed).isEmpty()) return;
        DecorationSavedData data = DecorationSavedData.get(level);
        boolean changed = false;
        for (DecorationInstance instance : data.within(pos, Decorations.maxRadius())) {
            if (!instance.involves(pos)) continue;
            DecorationDefinition definition = Decorations.definition(instance.decorationId());
            if (definition == null || evaluate(level, definition, instance.anchor(), pos) == null) {
                data.remove(instance.anchor());
                changed = true;
                DecorationDefinition smaller = definition == null ? null : fallback(level, definition, instance.anchor(), pos, data);
                if (by instanceof ServerPlayer player && definition != null) {
                    player.displayClientMessage(smaller == null
                            ? Component.translatable("townstead.decoration.dismantled", Component.translatable(definition.translationKey()))
                            : Component.translatable("townstead.decoration.reverted", Component.translatable(definition.translationKey()),
                                    Component.translatable(smaller.translationKey())), true);
                }
            }
        }
        if (changed) updateVillages(level, pos, by);
    }

    /** A decoration can decide a building's type (a Throne makes a Keep), so rooms around it are re-checked. */
    private static void updateVillages(ServerLevel level, BlockPos changed, @Nullable Entity by) {
        for (var village : net.conczin.mca.server.world.data.VillageManager.get(level)) {
            boolean retyped = false;
            for (var building : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
                BlockPos min = building.getPos0(), max = building.getPos1();
                if (changed.getX() < min.getX() || changed.getX() > max.getX() || changed.getY() < min.getY()
                        || changed.getY() > max.getY() || changed.getZ() < min.getZ() || changed.getZ() > max.getZ()) continue;
                String before = building.getType();
                var failure = com.aetherianartificer.townstead.upgrade.BuildingTierReconciler.applyChecks(village, level, building);
                if (building.getType() != null && !building.getType().equals(before)) {
                    retyped = true;
                    if (by instanceof ServerPlayer player) {
                        player.displayClientMessage(Component.translatable("townstead.building_check.now",
                                Component.translatable("buildingType." + building.getType())), false);
                    }
                } else if (failure != null && by instanceof ServerPlayer player) {
                    player.displayClientMessage(failure.message(), false);
                }
            }
            if (retyped) village.markDirty();
        }
        updateSpirits(level, changed);
    }

    private static void updateSpirits(ServerLevel level, BlockPos changed) {
        int margin = 24 + Decorations.maxRadius();
        for (var village : net.conczin.mca.server.world.data.VillageManager.get(level)) {
            var box = village.getBox();
            if (changed.getX() < box.minX() - margin || changed.getX() > box.maxX() + margin
                    || changed.getZ() < box.minZ() - margin || changed.getZ() > box.maxZ() + margin) continue;
            com.aetherianartificer.townstead.spirit.SpiritReconciler.reconcileVillage(level, village);
        }
    }

    /** Anchor blocks of the definition within reach of the changed block; the block itself when it is one. */
    private static List<BlockPos> anchorsNear(ServerLevel level, DecorationDefinition definition, BlockPos pos, BlockState placed) {
        List<BlockPos> anchors = new ArrayList<>();
        if (definition.isAnchor(placed)) anchors.add(pos.immutable());
        int r = definition.radius();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (definition.isAnchor(level.getBlockState(cursor))) anchors.add(cursor.immutable());
                }
            }
        }
        return anchors;
    }

    /** When a bigger set is dismantled, the smaller set it included takes its anchor back if it still stands. */
    private static @Nullable DecorationDefinition fallback(ServerLevel level, DecorationDefinition bigger, BlockPos anchor,
                                                           BlockPos removed, DecorationSavedData data) {
        for (ResourceLocation id : bigger.supersedes()) {
            DecorationDefinition smaller = Decorations.definition(id);
            DecorationInstance instance = smaller == null ? null : evaluate(level, smaller, anchor, removed);
            if (instance != null) {
                data.put(instance);
                return smaller;
            }
        }
        return null;
    }

    /**
     * The set at this anchor when every requirement is met, or null. {@code ignore} is treated as
     * air. The anchor of another set never counts as a member.
     */
    public static @Nullable DecorationInstance evaluate(ServerLevel level, DecorationDefinition definition,
                                                       BlockPos anchor, @Nullable BlockPos ignore) {
        if (!level.isLoaded(anchor) || anchor.equals(ignore)) return null;
        if (!definition.isAnchor(level.getBlockState(anchor))) return null;
        int r = definition.radius();
        for (DecorationDefinition.Variant variant : definition.variants()) {
            DecorationInstance instance = evaluateVariant(level, definition, variant, anchor, ignore);
            if (instance != null) return instance;
        }
        return null;
    }

    private static @Nullable DecorationInstance evaluateVariant(ServerLevel level, DecorationDefinition definition,
                                                                DecorationDefinition.Variant variant,
                                                                BlockPos anchor, @Nullable BlockPos ignore) {
        if (!variant.isAnchor(level.getBlockState(anchor))) return null;
        DecorationSavedData recognized = DecorationSavedData.get(level);
        int r = definition.radius();
        int n = variant.requires().size();
        int[] found = new int[n];
        List<List<BlockPos>> matched = new ArrayList<>(n);
        for (int i = 0; i < n; i++) matched.add(new ArrayList<>());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
                    if (cursor.equals(anchor) || cursor.equals(ignore)) continue;
                    DecorationInstance owner = recognized.at(cursor);
                    if (owner != null && !owner.decorationId().equals(definition.id())) continue;
                    BlockState state = level.getBlockState(cursor);
                    for (int i = 0; i < n; i++) {
                        DecorationDefinition.Requirement requirement = variant.requires().get(i);
                        if (found[i] < requirement.count() && requirement.matches(state)) {
                            found[i]++;
                            matched.get(i).add(cursor.immutable());
                            break;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (found[i] < variant.requires().get(i).count()) return null;
        }
        List<BlockPos> members = new ArrayList<>();
        for (List<BlockPos> group : matched) members.addAll(group);
        return new DecorationInstance(definition.id(), anchor.immutable(), List.copyOf(members));
    }

    private static void celebrate(ServerLevel level, DecorationDefinition definition, DecorationInstance instance, @Nullable Entity by) {
        BlockPos anchor = instance.anchor();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, anchor.getX() + 0.5, anchor.getY() + 1.0, anchor.getZ() + 0.5,
                12, 0.6, 0.5, 0.6, 0.02);
        for (BlockPos member : instance.members()) {
            level.sendParticles(ParticleTypes.END_ROD, member.getX() + 0.5, member.getY() + 1.1, member.getZ() + 0.5,
                    2, 0.2, 0.1, 0.2, 0.01);
        }
        level.playSound(null, anchor, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
        if (by instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("townstead.decoration.assembled",
                    Component.translatable(definition.translationKey())), true);
        }
    }
}
