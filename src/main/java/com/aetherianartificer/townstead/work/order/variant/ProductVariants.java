package com.aetherianartificer.townstead.work.order.variant;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every {@link VariantFamily}, from code and from data, and the products that leave an axis open.
 *
 * <p>A line that leaves an axis open ("wool, any colour", "any dried herb") has a pattern for its product:
 * {@code townstead_variant:<family namespace>/<family path>/<base namespace>/<base path>/<value>/<value>...}, one
 * value per axis of the base in order, {@link VariantAxis#ANY} where it is open. {@link #matches} reads a pattern
 * against a product key; every count and choice of work goes through it.</p>
 */
public final class ProductVariants {

    public static final String NAMESPACE = "townstead_variant";

    private static final List<VariantFamily> CODE = new CopyOnWriteArrayList<>();
    private static volatile List<VariantFamily> DATA = List.of();
    private static volatile Map<ResourceLocation, VariantAxis> AXES = Map.of();

    private ProductVariants() {}

    /** Adds a family that code describes. Data families with the same id replace it. */
    public static void register(VariantFamily family) {
        CODE.removeIf(f -> f.id().equals(family.id()));
        CODE.add(family);
    }

    /** The data's families and axes, after a reload. */
    static void replaceData(List<VariantFamily> families, Map<ResourceLocation, VariantAxis> axes) {
        DATA = List.copyOf(families);
        AXES = Map.copyOf(axes);
    }

    static void clearForTest() {
        CODE.clear();
        DATA = List.of();
        AXES = Map.of();
    }

    /** An axis declared in data, for families in code that use it too (a colour, say). */
    public static @Nullable VariantAxis axis(ResourceLocation id) {
        return AXES.get(id);
    }

    public static List<VariantFamily> families() {
        List<VariantFamily> out = new ArrayList<>(DATA);
        for (VariantFamily f : CODE) if (out.stream().noneMatch(d -> d.id().equals(f.id()))) out.add(f);
        return out;
    }

    public static @Nullable VariantFamily family(ResourceLocation id) {
        for (VariantFamily f : DATA) if (f.id().equals(id)) return f;
        for (VariantFamily f : CODE) if (f.id().equals(id)) return f;
        return null;
    }

    /** The family, base and values of a product, or null if no family describes it. */
    public static @Nullable VariantFamily.Described describe(@Nullable ResourceLocation product) {
        if (product == null || isPattern(product)) return null;
        for (VariantFamily f : families()) {
            try {
                VariantFamily.Described d = f.describe(product);
                if (d != null) return d;
            } catch (RuntimeException ignored) {
                // a family that cannot read this product does not describe it
            }
        }
        return null;
    }

    // --- open lines ---

    public static boolean isPattern(@Nullable ResourceLocation product) {
        return product != null && NAMESPACE.equals(product.getNamespace());
    }

    /** A pattern read back: its family, base, and each axis's value or {@link VariantAxis#ANY}. */
    public record Pattern(VariantFamily family, ResourceLocation base, Map<ResourceLocation, String> values) {}

    /** The product for this base and these values; an open axis makes it a pattern, none open the exact product. */
    public static @Nullable ResourceLocation product(ResourceLocation family, ResourceLocation base, Map<ResourceLocation, String> values) {
        VariantFamily f = family(family);
        if (f == null) return null;
        List<VariantAxis> axes = f.axes(base);
        boolean open = false;
        for (VariantAxis axis : axes) {
            String v = values.get(axis.id());
            if (v == null || VariantAxis.ANY.equals(v)) {
                if (!axis.any()) return null;
                open = true;
            }
        }
        if (!open) return f.compose(base, values);
        StringBuilder path = new StringBuilder(family.getNamespace()).append('/').append(family.getPath())
                .append('/').append(base.getNamespace()).append('/').append(base.getPath());
        for (VariantAxis axis : axes) {
            String v = values.get(axis.id());
            path.append('/').append(v == null ? VariantAxis.ANY : v);
        }
        return ResourceLocation.tryParse(NAMESPACE + ":" + path);
    }

    public static @Nullable Pattern parse(@Nullable ResourceLocation pattern) {
        if (!isPattern(pattern)) return null;
        String[] parts = pattern.getPath().split("/");
        if (parts.length < 5) return null;
        VariantFamily f = family(ResourceLocation.tryParse(parts[0] + ":" + parts[1]));
        if (f == null) return null;
        // the base's path may hold "/" itself: the family's axes say how many values end the key
        for (int valueCount = 1; valueCount <= parts.length - 4; valueCount++) {
            String basePath = String.join("/", java.util.Arrays.copyOfRange(parts, 3, parts.length - valueCount));
            ResourceLocation base = ResourceLocation.tryParse(parts[2] + ":" + basePath);
            if (base == null) continue;
            List<VariantAxis> axes;
            try {
                axes = f.axes(base);
            } catch (RuntimeException e) {
                continue;
            }
            if (axes.size() != valueCount) continue;
            Map<ResourceLocation, String> values = new LinkedHashMap<>();
            for (int i = 0; i < valueCount; i++) values.put(axes.get(i).id(), parts[parts.length - valueCount + i]);
            return new Pattern(f, base, values);
        }
        return null;
    }

    /**
     * Whether a line's product takes in {@code candidate}: the same product, or for a pattern, a product its family
     * describes as the same base with the same value on every axis the pattern does not leave open.
     */
    public static boolean matches(@Nullable ResourceLocation product, @Nullable ResourceLocation candidate) {
        if (product == null || candidate == null) return false;
        if (!isPattern(product)) return product.equals(candidate);
        Pattern pattern = parse(product);
        if (pattern == null) return false;
        VariantFamily.Described d;
        try {
            d = pattern.family().describe(candidate);
        } catch (RuntimeException e) {
            return false;
        }
        if (d == null || !d.base().equals(pattern.base())) return false;
        for (Map.Entry<ResourceLocation, String> e : pattern.values().entrySet()) {
            if (VariantAxis.ANY.equals(e.getValue())) continue;
            if (!e.getValue().equals(d.values().get(e.getKey()))) return false;
        }
        return true;
    }
}
