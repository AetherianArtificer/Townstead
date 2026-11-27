package com.aetherianartificer.townstead.work.order.variant;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Things that are one base thing in several variants: an herb fresh, dried or charred; a potion of Healing as a drink
 * or a splash, normal or strong; wool in sixteen colours. A family reads a product key as its base and the value it
 * takes on each axis, and back. The Order Sheet then shows the base once and lets the variants be chosen on the line.
 *
 * <p>Families come from code ({@link ProductVariants#register}) or from data ({@code townstead/variants/*.json}).</p>
 */
public interface VariantFamily {

    /** The family's id. Its path may not contain {@code /}: it is part of pattern keys. */
    ResourceLocation id();

    /** The base and values of {@code product}, or null if this family does not describe it. */
    @Nullable Described describe(ResourceLocation product);

    /** The product a base takes with these values (every axis given, none open), or null if it does not exist. */
    @Nullable ResourceLocation compose(ResourceLocation base, Map<ResourceLocation, String> values);

    /** The axes this base varies on, in the order they are chosen. */
    List<VariantAxis> axes(ResourceLocation base);

    /** The base's name as a translation key or plain text, and an item to show for it. */
    String label(ResourceLocation base);

    @Nullable ResourceLocation icon(ResourceLocation base);

    /**
     * A product read as its family, base and the value it takes on each axis.
     *
     * @param values axis id to value id; every axis of the base, in order
     */
    record Described(ResourceLocation family, ResourceLocation base, Map<ResourceLocation, String> values) {}
}
