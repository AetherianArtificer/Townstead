package com.aetherianartificer.townstead.pheno.action.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The block a {@link BlockAction} runs at: a {@link ServerLevel} and {@link BlockPos},
 * plus the optional {@code cause} entity that triggered it (for command source /
 * spawned-entity ownership). The {@code offset} and {@code area_of_effect} metas derive
 * new positions via {@link #at}.
 */
public final class BlockActionContext {

    private final ServerLevel level;
    private final BlockPos pos;
    private final LivingEntity cause;
    private final Outcome outcome;
    private final Map<String, ItemStack> itemRoles;
    private final Map<String, List<BlockPos>> blockRoles;
    private final List<ItemStack> returnedItems;

    public BlockActionContext(ServerLevel level, BlockPos pos) {
        this(level, pos, null);
    }

    public BlockActionContext(ServerLevel level, BlockPos pos, @Nullable LivingEntity cause) {
        this(level, pos, cause, new Outcome(), new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>());
    }

    private BlockActionContext(ServerLevel level, BlockPos pos, @Nullable LivingEntity cause, Outcome outcome,
                               Map<String, ItemStack> itemRoles, List<ItemStack> returnedItems) {
        this(level, pos, cause, outcome, itemRoles, new LinkedHashMap<>(), returnedItems);
    }

    private BlockActionContext(ServerLevel level, BlockPos pos, @Nullable LivingEntity cause, Outcome outcome,
                               Map<String, ItemStack> itemRoles, Map<String, List<BlockPos>> blockRoles,
                               List<ItemStack> returnedItems) {
        this.level = level;
        this.pos = pos;
        this.cause = cause;
        this.outcome = outcome;
        this.itemRoles = itemRoles;
        this.blockRoles = blockRoles;
        this.returnedItems = returnedItems;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    @Nullable
    public LivingEntity cause() {
        return cause;
    }

    /** The same context at a different block (used by offset / area-of-effect). */
    public BlockActionContext at(BlockPos newPos) {
        return new BlockActionContext(level, newPos, cause, outcome, itemRoles, blockRoles, returnedItems);
    }

    public BlockActionContext withItemRole(String role, ItemStack stack) {
        itemRoles.put(role, stack == null ? ItemStack.EMPTY : stack);
        return this;
    }

    public ItemStack itemRole(String role) { return itemRoles.getOrDefault(role, ItemStack.EMPTY); }

    public void setItemRole(String role, ItemStack stack) {
        itemRoles.put(role, stack == null ? ItemStack.EMPTY : stack);
    }

    public BlockActionContext withBlockRoles(Map<String, List<BlockPos>> roles) {
        blockRoles.clear();
        if (roles != null) blockRoles.putAll(roles);
        return this;
    }

    public Map<String, List<BlockPos>> blockRoles() { return Map.copyOf(blockRoles); }

    public void returnItem(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) returnedItems.add(stack);
    }

    public List<ItemStack> returnedItems() { return List.copyOf(returnedItems); }

    public void fail() { outcome.successful = false; }

    public boolean succeeded() { return outcome.successful; }

    private static final class Outcome { private boolean successful = true; }
}
