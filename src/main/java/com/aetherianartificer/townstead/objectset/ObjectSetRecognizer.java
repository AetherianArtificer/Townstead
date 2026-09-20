package com.aetherianartificer.townstead.objectset;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
public final class ObjectSetRecognizer {
    private ObjectSetRecognizer() {}

    /** Import naturally generated object sets when the player refreshes a village. */
    public static int reconcileVillage(ServerLevel level, net.conczin.mca.server.world.data.Village village) {
        if (level == null || village == null || ObjectSets.isEmpty()) return 0;
        BlockPos center = new BlockPos(village.getCenter().getX(), village.getCenter().getY(),
                village.getCenter().getZ());
        var box = village.getBox();
        int villageReach = Math.max(Math.max(center.getX() - box.minX(), box.maxX() - center.getX()),
                Math.max(center.getZ() - box.minZ(), box.maxZ() - center.getZ()));
        int radius = Math.min(160, Math.max(48, villageReach + 24));
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;
        ObjectSetSavedData data = ObjectSetSavedData.get(level);
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
                                for (ObjectSetDefinition definition : ObjectSets.all()) {
                                    if (!definition.isAnchor(state)) continue;
                                    ObjectSetInstance instance = evaluate(level, definition, anchor, null);
                                    if (instance == null) continue;
                                    if (duplicatesNearby(data, definition, instance)) continue;
                                    live.add(anchor.asLong());
                                    ObjectSetInstance old = data.at(anchor);
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
        for (ObjectSetInstance instance : data.within(center, radius * 2)) {
            BlockPos anchor = instance.anchor();
            if (anchor.getX() < minX || anchor.getX() > maxX || anchor.getZ() < minZ || anchor.getZ() > maxZ) continue;
            if (level.isLoaded(anchor) && !live.contains(anchor.asLong())) data.remove(anchor);
        }
        return added;
    }

    public static void onPlaced(ServerLevel level, BlockPos pos, BlockState placed, @Nullable Entity by) {
        if (ObjectSets.isEmpty()) return;
        List<ObjectSetDefinition> candidates = ObjectSets.touching(placed);
        if (candidates.isEmpty()) return;
        ObjectSetSavedData data = ObjectSetSavedData.get(level);
        for (ObjectSetDefinition definition : candidates) {
            for (BlockPos anchor : anchorsNear(level, definition, pos, placed)) {
                if (data.at(anchor) != null) continue;
                ObjectSetInstance instance = evaluate(level, definition, anchor, null);
                if (instance == null) continue;
                if (duplicatesNearby(data, definition, instance)) continue;
                data.put(instance);
                celebrate(level, definition, instance, by);
            }
        }
    }

    /** Multi-anchor assemblies (a four-lantern post or a haystack) are one set, not four. */
    private static boolean duplicatesNearby(ObjectSetSavedData data, ObjectSetDefinition definition,
                                            ObjectSetInstance candidate) {
        Set<Long> members = new HashSet<>();
        candidate.members().forEach(pos -> members.add(pos.asLong()));
        for (ObjectSetInstance existing : data.within(candidate.anchor(), definition.radius() * 2)) {
            if (!existing.setId().equals(candidate.setId())) continue;
            if (existing.anchor().equals(candidate.anchor())) return false;
            if (members.contains(existing.anchor().asLong())
                    || existing.members().stream().anyMatch(pos -> members.contains(pos.asLong()))) {
                return true;
            }
        }
        return false;
    }

    public static void onRemoved(ServerLevel level, BlockPos pos, BlockState removed, @Nullable Entity by) {
        if (ObjectSets.isEmpty() || ObjectSets.touching(removed).isEmpty()) return;
        ObjectSetSavedData data = ObjectSetSavedData.get(level);
        for (ObjectSetInstance instance : data.within(pos, ObjectSets.maxRadius())) {
            if (!instance.involves(pos)) continue;
            ObjectSetDefinition definition = ObjectSets.definition(instance.setId());
            if (definition == null || evaluate(level, definition, instance.anchor(), pos) == null) {
                data.remove(instance.anchor());
                if (by instanceof ServerPlayer player && definition != null) {
                    player.displayClientMessage(Component.translatable("townstead.object_set.dismantled",
                            Component.translatable(definition.translationKey())), true);
                }
            }
        }
    }

    /** Anchor blocks of the definition within reach of the changed block; the block itself when it is one. */
    private static List<BlockPos> anchorsNear(ServerLevel level, ObjectSetDefinition definition, BlockPos pos, BlockState placed) {
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

    /** The set at this anchor when every requirement is met, or null. {@code ignore} is treated as air. */
    public static @Nullable ObjectSetInstance evaluate(ServerLevel level, ObjectSetDefinition definition,
                                                       BlockPos anchor, @Nullable BlockPos ignore) {
        if (!level.isLoaded(anchor) || anchor.equals(ignore)) return null;
        if (!definition.isAnchor(level.getBlockState(anchor))) return null;
        int r = definition.radius();
        for (ObjectSetDefinition.Variant variant : definition.variants()) {
            ObjectSetInstance instance = evaluateVariant(level, definition, variant, anchor, ignore);
            if (instance != null) return instance;
        }
        return null;
    }

    private static @Nullable ObjectSetInstance evaluateVariant(ServerLevel level, ObjectSetDefinition definition,
                                                                ObjectSetDefinition.Variant variant,
                                                                BlockPos anchor, @Nullable BlockPos ignore) {
        if (!variant.isAnchor(level.getBlockState(anchor))) return null;
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
                    BlockState state = level.getBlockState(cursor);
                    for (int i = 0; i < n; i++) {
                        ObjectSetDefinition.Requirement requirement = variant.requires().get(i);
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
        return new ObjectSetInstance(definition.id(), anchor.immutable(), List.copyOf(members));
    }

    private static void celebrate(ServerLevel level, ObjectSetDefinition definition, ObjectSetInstance instance, @Nullable Entity by) {
        BlockPos anchor = instance.anchor();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, anchor.getX() + 0.5, anchor.getY() + 1.0, anchor.getZ() + 0.5,
                12, 0.6, 0.5, 0.6, 0.02);
        for (BlockPos member : instance.members()) {
            level.sendParticles(ParticleTypes.END_ROD, member.getX() + 0.5, member.getY() + 1.1, member.getZ() + 0.5,
                    2, 0.2, 0.1, 0.2, 0.01);
        }
        level.playSound(null, anchor, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
        if (by instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("townstead.object_set.assembled",
                    Component.translatable(definition.translationKey())), true);
        }
    }
}
