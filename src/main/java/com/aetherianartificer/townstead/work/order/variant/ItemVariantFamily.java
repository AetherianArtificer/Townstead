package com.aetherianartificer.townstead.work.order.variant;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A family declared in data whose variants are plain items: white, red and blue wool are three item ids, one base
 * with a colour. Each member is an item and the value it takes on each axis.
 */
final class ItemVariantFamily implements VariantFamily {

    record Member(Map<ResourceLocation, String> values, ResourceLocation item) {}

    private final ResourceLocation id;
    private final ResourceLocation base;
    private final String label;
    private final @Nullable ResourceLocation icon;
    private final List<VariantAxis> axes;
    private final Map<ResourceLocation, Member> byItem = new HashMap<>();
    private final Map<Map<ResourceLocation, String>, ResourceLocation> byValues = new HashMap<>();

    ItemVariantFamily(ResourceLocation id, ResourceLocation base, String label, @Nullable ResourceLocation icon,
                      List<VariantAxis> axes, List<Member> members) {
        this.id = id;
        this.base = base;
        this.label = label;
        this.icon = icon;
        this.axes = List.copyOf(axes);
        for (Member m : members) {
            byItem.put(m.item(), m);
            byValues.put(new LinkedHashMap<>(m.values()), m.item());
        }
    }

    List<Member> members() {
        return List.copyOf(byItem.values());
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public @Nullable Described describe(ResourceLocation product) {
        Member m = byItem.get(product);
        return m == null ? null : new Described(id, base, m.values());
    }

    @Override
    public @Nullable ResourceLocation compose(ResourceLocation base, Map<ResourceLocation, String> values) {
        if (!this.base.equals(base)) return null;
        Map<ResourceLocation, String> key = new LinkedHashMap<>();
        for (VariantAxis axis : axes) key.put(axis.id(), values.get(axis.id()));
        return byValues.get(key);
    }

    @Override
    public List<VariantAxis> axes(ResourceLocation base) {
        return this.base.equals(base) ? axes : List.of();
    }

    @Override
    public String label(ResourceLocation base) {
        return label;
    }

    @Override
    public @Nullable ResourceLocation icon(ResourceLocation base) {
        return icon;
    }
}
