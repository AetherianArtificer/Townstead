package com.aetherianartificer.townstead.pheno.action.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The stack an {@link ItemAction} operates on, plus the optional {@code holder} entity
 * (for {@code holder_action} and durability damage). Item actions mutate the stack in
 * place, so the caller's slot reflects the change.
 */
public final class ItemActionContext {

    private final ItemStack stack;
    private final LivingEntity holder;
    private final @Nullable com.aetherianartificer.townstead.pheno.action.ActionContext actionContext;

    public ItemActionContext(ItemStack stack) {
        this(stack, null);
    }

    public ItemActionContext(ItemStack stack, @Nullable LivingEntity holder) {
        this(stack, holder, null);
    }

    public ItemActionContext(ItemStack stack, @Nullable LivingEntity holder,
                             @Nullable com.aetherianartificer.townstead.pheno.action.ActionContext actionContext) {
        this.stack = stack;
        this.holder = holder;
        this.actionContext = actionContext;
    }

    public ItemStack stack() {
        return stack;
    }

    @Nullable
    public LivingEntity holder() {
        return holder;
    }

    public @Nullable com.aetherianartificer.townstead.pheno.action.ActionContext actionContext() {
        return actionContext;
    }
}
