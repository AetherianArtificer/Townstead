package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Immutable-by-copy exact item request, including components/NBT and quantity. */
public final class ExactServiceProduct {
    private final ResourceLocation item;
    private final ResourceLocation product;
    private final int quantity;
    private final ItemStack template;

    public ExactServiceProduct(ItemStack template, ResourceLocation product) {
        if (template == null || template.isEmpty()) throw new IllegalArgumentException("template is required");
        this.item = BuiltInRegistries.ITEM.getKey(template.getItem());
        this.template = template.copy();
        this.product = Objects.requireNonNull(product, "product");
        this.quantity = template.getCount();
    }

    private ExactServiceProduct(ResourceLocation item, ResourceLocation product, int quantity) {
        this.item = Objects.requireNonNull(item, "item");
        this.product = Objects.requireNonNull(product, "product");
        if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
        this.quantity = quantity;
        this.template = null;
    }

    public static ExactServiceProduct item(ItemStack template) {
        return new ExactServiceProduct(template, BuiltInRegistries.ITEM.getKey(template.getItem()));
    }

    /** Data/network descriptor when components are not required by the provider. */
    public static ExactServiceProduct descriptor(ResourceLocation item, ResourceLocation product, int quantity) {
        return new ExactServiceProduct(item, product, quantity);
    }

    public ItemStack template() { return template == null ? ItemStack.EMPTY : template.copy(); }
    public ResourceLocation item() { return item; }
    public ResourceLocation product() { return product; }
    public int quantity() { return quantity; }
    public boolean hasComponentTemplate() { return template != null; }

    public boolean matches(ItemStack candidate) {
        if (candidate == null || candidate.isEmpty() || candidate.getCount() < quantity()) return false;
        if (!BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(item)) return false;
        if (template == null) return true;
        //? if >=1.21 {
        return ItemStack.isSameItemSameComponents(template, candidate);
        //?} else {
        /*return ItemStack.isSameItemSameTags(template, candidate);
        *///?}
    }
}
