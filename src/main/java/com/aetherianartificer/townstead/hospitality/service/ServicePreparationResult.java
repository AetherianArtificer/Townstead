package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.world.item.ItemStack;

/** Result of native provider-owned preparation. The provider commits the offered input stack. */
public record ServicePreparationResult(Status status, int accepted, ItemStack output, String detail) {
    public enum Status { SUCCESS, REFUSED, CANCELLED, UNSUPPORTED, ERROR }

    public ServicePreparationResult {
        if (accepted < 0) throw new IllegalArgumentException("accepted cannot be negative");
        output = output == null ? ItemStack.EMPTY : output.copy();
        detail = detail == null ? "" : detail;
        if (status == Status.SUCCESS && (accepted < 1 || output.isEmpty())) {
            throw new IllegalArgumentException("successful preparation needs input and output");
        }
        if (status != Status.SUCCESS && accepted != 0) {
            throw new IllegalArgumentException("rejected preparation cannot accept input");
        }
    }

    public static ServicePreparationResult success(int accepted, ItemStack output) {
        return new ServicePreparationResult(Status.SUCCESS, accepted, output, "");
    }

    public static ServicePreparationResult rejected(Status status, String detail) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("use success for SUCCESS");
        return new ServicePreparationResult(status, 0, ItemStack.EMPTY, detail);
    }
}
