package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.world.item.ItemStack;

/** Result of provider-owned cleanup/return work. */
public record ServiceFollowupCompletion(Status status, ItemStack output, String detail) {
    public enum Status { SUCCESS, REFUSED, ALREADY_COMPLETED, CANCELLED, UNSUPPORTED, ERROR }

    public ServiceFollowupCompletion {
        output = output == null ? ItemStack.EMPTY : output.copy();
        detail = detail == null ? "" : detail;
    }

    public static ServiceFollowupCompletion success(ItemStack output) {
        return new ServiceFollowupCompletion(Status.SUCCESS, output, "");
    }

    public static ServiceFollowupCompletion rejected(Status status, String detail) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("use success for SUCCESS");
        return new ServiceFollowupCompletion(status, ItemStack.EMPTY, detail);
    }
}
