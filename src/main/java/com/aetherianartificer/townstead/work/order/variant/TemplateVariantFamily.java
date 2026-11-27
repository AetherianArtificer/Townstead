package com.aetherianartificer.townstead.work.order.variant;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * A family declared in data whose variants are product keys spelled from a template: any base, any value.
 * {@code "malrem_product:prepared/{malrem:method}/{base.namespace}/{base.path}"} makes every herb a base, and its
 * dried and charred keys the variants of it. Placeholders are {@code {base.namespace}}, {@code {base.path}} and
 * {@code {<axis id>}}; each must appear once.
 */
final class TemplateVariantFamily implements VariantFamily {

    private final ResourceLocation id;
    private final String template;
    private final @Nullable String labelTemplate;
    private final @Nullable String iconTemplate;
    private final List<VariantAxis> axes;
    private final java.util.regex.Pattern regex;
    /** Which group of the regex holds what: "ns", "path", or an axis id. */
    private final List<String> groups = new ArrayList<>();

    private TemplateVariantFamily(ResourceLocation id, String template, @Nullable String labelTemplate,
                                  @Nullable String iconTemplate, List<VariantAxis> axes, java.util.regex.Pattern regex,
                                  List<String> groups) {
        this.id = id;
        this.template = template;
        this.labelTemplate = labelTemplate;
        this.iconTemplate = iconTemplate;
        this.axes = List.copyOf(axes);
        this.regex = regex;
        this.groups.addAll(groups);
    }

    /** The family, or null when the template does not hold each placeholder exactly once. */
    static @Nullable TemplateVariantFamily of(ResourceLocation id, String template, List<VariantAxis> axes) {
        return of(id, template, null, null, axes);
    }

    /**
     * With a label and an icon spelled from the base too, for a base that is not an item: a potion's
     * {@code "item.minecraft.potion.effect.{base.path}"}.
     */
    static @Nullable TemplateVariantFamily of(ResourceLocation id, String template, @Nullable String labelTemplate,
                                              @Nullable String iconTemplate, List<VariantAxis> axes) {
        StringBuilder regex = new StringBuilder();
        List<String> groups = new ArrayList<>();
        int at = 0;
        while (at < template.length()) {
            int open = template.indexOf('{', at);
            if (open < 0) {
                regex.append(java.util.regex.Pattern.quote(template.substring(at)));
                break;
            }
            int close = template.indexOf('}', open);
            if (close < 0) return null;
            if (open > at) regex.append(java.util.regex.Pattern.quote(template.substring(at, open)));
            String name = template.substring(open + 1, close);
            if (groups.contains(name)) return null;
            switch (name) {
                case "base.namespace" -> regex.append("([a-z0-9_.-]+)");
                case "base.path" -> regex.append("([a-z0-9_./-]+?)");
                default -> {
                    VariantAxis axis = axisNamed(axes, name);
                    if (axis == null) return null;
                    // longest spelling first, so a "strong_" prefix is not read as part of the base
                    List<VariantAxis.Value> bySpelling = new ArrayList<>(axis.values());
                    bySpelling.sort((x, y) -> y.spelled().length() - x.spelled().length());
                    List<String> alternatives = new ArrayList<>();
                    for (VariantAxis.Value v : bySpelling) alternatives.add(java.util.regex.Pattern.quote(v.spelled()));
                    regex.append('(').append(String.join("|", alternatives)).append(')');
                }
            }
            groups.add(name);
            at = close + 1;
        }
        if (!groups.contains("base.namespace") || !groups.contains("base.path")) return null;
        for (VariantAxis axis : axes) if (!groups.contains(axis.id().toString())) return null;
        return new TemplateVariantFamily(id, template, labelTemplate, iconTemplate, axes,
                java.util.regex.Pattern.compile(regex.toString()), groups);
    }

    private static @Nullable VariantAxis axisNamed(List<VariantAxis> axes, String name) {
        for (VariantAxis axis : axes) if (axis.id().toString().equals(name)) return axis;
        return null;
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public @Nullable Described describe(ResourceLocation product) {
        Matcher m = regex.matcher(product.toString());
        if (!m.matches()) return null;
        String ns = null, path = null;
        Map<ResourceLocation, String> values = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            String g = m.group(i + 1);
            switch (groups.get(i)) {
                case "base.namespace" -> ns = g;
                case "base.path" -> path = g;
                default -> {
                    VariantAxis axis = axisNamed(axes, groups.get(i));
                    String id = null;
                    if (axis != null) for (VariantAxis.Value v : axis.values()) if (v.spelled().equals(g)) id = v.id();
                    if (id == null) return null;
                    values.put(axis.id(), id);
                }
            }
        }
        ResourceLocation base = ResourceLocation.tryParse(ns + ":" + path);
        if (base == null) return null;
        Map<ResourceLocation, String> ordered = new LinkedHashMap<>();
        for (VariantAxis axis : axes) ordered.put(axis.id(), values.get(axis.id()));
        return new Described(id, base, ordered);
    }

    @Override
    public @Nullable ResourceLocation compose(ResourceLocation base, Map<ResourceLocation, String> values) {
        String out = template.replace("{base.namespace}", base.getNamespace()).replace("{base.path}", base.getPath());
        for (VariantAxis axis : axes) {
            String v = values.get(axis.id());
            if (v == null || axis.value(v) == null) return null;
            out = out.replace("{" + axis.id() + "}", axis.value(v).spelled());
        }
        return ResourceLocation.tryParse(out);
    }

    @Override
    public List<VariantAxis> axes(ResourceLocation base) {
        return axes;
    }

    /** The base item's own name, which the screen translates. */
    private static String spell(String template, ResourceLocation base) {
        return template.replace("{base.namespace}", base.getNamespace()).replace("{base.path}", base.getPath());
    }

    @Override
    public String label(ResourceLocation base) {
        if (labelTemplate != null) return spell(labelTemplate, base);
        return BuiltInRegistries.ITEM.containsKey(base)
                ? BuiltInRegistries.ITEM.get(base).getDescriptionId()
                : base.toString();
    }

    @Override
    public @Nullable ResourceLocation icon(ResourceLocation base) {
        if (iconTemplate != null) return ResourceLocation.tryParse(spell(iconTemplate, base));
        return BuiltInRegistries.ITEM.containsKey(base) ? base : null;
    }
}
