package com.aetherianartificer.townstead.client.gui.orders;

import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.client.gui.common.Controls.Rect;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Family;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Member;
import com.aetherianartificer.townstead.work.order.variant.ProductVariants;
import com.aetherianartificer.townstead.work.order.variant.VariantAxis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Order Sheet's variants, as the screen uses them: which family and values a line's product is, the product a
 * choice of values makes, which values can be chosen given the others, a line's name, and rows of choices that wrap.
 * Any screen that offers variants (a line, a draft, its details) goes through here.
 */
public final class VariantChoices {

    /** The values a row's choices hold: one per axis, {@link VariantAxis#ANY} where it is left open. */
    public record Choice(Family family, String[] values) {}

    private VariantChoices() {}

    /** The family and values of a product: a member offered here, or a pattern over one of these families. */
    public static @Nullable Choice of(List<Family> families, @Nullable ResourceLocation product) {
        if (product == null) return null;
        for (Family family : families) {
            for (Member member : family.members()) {
                if (member.product().equals(product)) return new Choice(family, member.values().toArray(String[]::new));
            }
        }
        if (!ProductVariants.isPattern(product)) return null;
        for (Family family : families) {
            String[] values = patternValues(family, product);
            if (values != null) return new Choice(family, values);
        }
        return null;
    }

    /** The values a pattern holds, if it is a pattern over this family's base. */
    private static @Nullable String[] patternValues(Family family, ResourceLocation pattern) {
        String prefix = family.family().getNamespace() + "/" + family.family().getPath() + "/"
                + family.base().getNamespace() + "/" + family.base().getPath() + "/";
        if (!pattern.getPath().startsWith(prefix)) return null;
        String[] values = pattern.getPath().substring(prefix.length()).split("/");
        return values.length == family.axes().size() ? values : null;
    }

    /** The values a new line starts with: the first member offered here and workable, else the first at all. */
    public static String[] defaults(Family family) {
        for (Member member : family.members()) if (member.available()) return member.values().toArray(String[]::new);
        return family.members().isEmpty() ? new String[family.axes().size()]
                : family.members().get(0).values().toArray(String[]::new);
    }

    /** The product these values make: the member they name, or a pattern when an axis is left open. */
    public static @Nullable ResourceLocation product(Family family, String[] values) {
        boolean open = false;
        for (String v : values) if (v == null || VariantAxis.ANY.equals(v)) open = true;
        if (!open) {
            Member m = member(family, values);
            return m == null ? null : m.product();
        }
        StringBuilder path = new StringBuilder(family.family().getNamespace()).append('/').append(family.family().getPath())
                .append('/').append(family.base().getNamespace()).append('/').append(family.base().getPath());
        for (String v : values) path.append('/').append(v == null ? VariantAxis.ANY : v);
        return ResourceLocation.tryParse(ProductVariants.NAMESPACE + ":" + path);
    }

    public static @Nullable Member member(Family family, String[] values) {
        for (Member m : family.members()) if (m.values().equals(Arrays.asList(values))) return m;
        return null;
    }

    /** Whether some member here has every one of these values, an open one matching any. */
    private static boolean offered(Family family, String[] values) {
        for (Member m : family.members()) {
            boolean all = true;
            for (int i = 0; i < values.length && all; i++) {
                String v = values[i];
                if (v != null && !VariantAxis.ANY.equals(v) && !v.equals(m.values().get(i))) all = false;
            }
            if (all) return true;
        }
        return false;
    }

    // --- a row of choices per axis ---

    /** The choices of an axis as the row shows them: Any first when the axis allows it. */
    public static List<String> choices(Family family, int axis) {
        var a = family.axes().get(axis);
        List<String> out = new ArrayList<>();
        if (a.any()) out.add(VariantAxis.ANY);
        for (var v : a.values()) out.add(v.id());
        return out;
    }

    public static String[] labels(Family family, int axis) {
        List<String> choices = choices(family, axis);
        String[] out = new String[choices.size()];
        for (int i = 0; i < out.length; i++) out[i] = valueName(family, axis, choices.get(i));
        return out;
    }

    /** Which of an axis's choices can be taken with the other axes as they are. */
    public static boolean[] enabled(Family family, String[] values, int axis) {
        List<String> choices = choices(family, axis);
        boolean[] out = new boolean[choices.size()];
        for (int i = 0; i < out.length; i++) {
            String[] probe = values.clone();
            probe[axis] = choices.get(i);
            out[i] = offered(family, probe);
        }
        return out;
    }

    public static int selected(Family family, String[] values, int axis) {
        return choices(family, axis).indexOf(values[axis] == null ? VariantAxis.ANY : values[axis]);
    }

    /**
     * Where an axis's choices go, wrapping onto further rows of {@link Controls#SEG_H} + 2 when they would run past
     * {@code maxW}. Each row starts at {@code x}.
     */
    public static Rect[] layout(Font font, int x, int y, int maxW, String[] labels) {
        Rect[] flat = Controls.segmentLayout(font, 0, 0, labels);
        Rect[] out = new Rect[flat.length];
        int cx = x, cy = y;
        for (int i = 0; i < flat.length; i++) {
            if (cx > x && cx - x + flat[i].w() > maxW) {
                cx = x;
                cy += Controls.SEG_H + 2;
            }
            out[i] = new Rect(cx, cy, flat[i].w(), flat[i].h());
            cx += flat[i].w();
        }
        return out;
    }

    /** How tall an axis's choices stand once laid out. */
    public static int height(Rect[] rects) {
        if (rects.length == 0) return 0;
        return rects[rects.length - 1].bottom() - rects[0].y();
    }

    // --- names ---

    /** "Oxeye Daisy · dried", "Healing · splash, strong", "Wool · any colour". */
    public static String name(Family family, String[] values) {
        StringBuilder out = new StringBuilder(baseName(family));
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < values.length && i < family.axes().size(); i++) {
            parts.add(valueName(family, i, values[i]).toLowerCase(java.util.Locale.ROOT));
        }
        if (!parts.isEmpty()) out.append(" · ").append(String.join(", ", parts));
        return out.toString();
    }

    public static String valueName(Family family, int axis, @Nullable String value) {
        if (value == null || VariantAxis.ANY.equals(value)) return I18n.get("townstead.orders.variant.any");
        for (var v : family.axes().get(axis).values()) if (v.id().equals(value)) return text(v.label());
        return value;
    }

    /** How the screen names an item or product, for a base whose own label this client cannot read. */
    public static java.util.function.Function<ResourceLocation, String> memberNames = ResourceLocation::toString;

    /** The base's name: its label, or, when that is a key this client lacks, the first variant's own name. */
    public static String baseName(Family family) {
        String label = family.label();
        if (I18n.exists(label)) return I18n.get(label);
        boolean key = !label.contains(" ") && label.contains(".");
        if (key && !family.members().isEmpty()) return memberNames.apply(family.members().get(0).product());
        return label;
    }

    /** A label that may be a translation key or plain text. */
    public static String text(String keyOrText) {
        return I18n.exists(keyOrText) ? I18n.get(keyOrText) : keyOrText;
    }
}
