package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.world.item.ItemStack;

/** Structured provider result; only SUCCESS authorizes XP/events and local item commit. */
public record ServiceFulfillment(Status status, int accepted, ItemStack returned, String detail) {
    public enum Status { SUCCESS, REFUSED, ALREADY_COMPLETED, CANCELLED, UNSUPPORTED, ERROR }

    public ServiceFulfillment {
        if (accepted < 0) throw new IllegalArgumentException("accepted cannot be negative");
        returned = returned == null ? ItemStack.EMPTY : returned.copy();
        detail = detail == null ? "" : detail;
        if (status != Status.SUCCESS && accepted != 0) {
            throw new IllegalArgumentException("only successful fulfillment may accept items");
        }
    }

    public static ServiceFulfillment success(int accepted, ItemStack returned) {
        if (accepted < 1) throw new IllegalArgumentException("success must accept at least one item");
        return new ServiceFulfillment(Status.SUCCESS, accepted, returned, "");
    }

    public static ServiceFulfillment rejected(Status status, String detail) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("use success for SUCCESS");
        return new ServiceFulfillment(status, 0, ItemStack.EMPTY, detail);
    }
}
