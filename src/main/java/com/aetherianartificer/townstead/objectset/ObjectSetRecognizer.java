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
import java.util.List;

/**
 * Recognises sets as blocks land and forgets them as blocks go. Placement: every definition the
 * block could belong to is tried around every anchor within reach. Removal: every recognised set
 * the block belonged to is re-checked with that block treated as air. A completed set sparkles,
 * chimes, and names itself on the builder's action bar, the way a builder game confirms a room.
 */
public final class ObjectSetRecognizer {
    private ObjectSetRecognizer() {}

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
                data.put(instance);
                celebrate(level, definition, instance, by);
            }
        }
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
        int n = definition.requires().size();
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
                        ObjectSetDefinition.Requirement requirement = definition.requires().get(i);
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
            if (found[i] < definition.requires().get(i).count()) return null;
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
