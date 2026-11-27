package com.aetherianartificer.townstead.work.order.variant;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code data/<namespace>/townstead/variants/*.json}: axes, and families of plain items that vary along them.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:variants/v1",
 *   "axes": [{"id": "townstead:colour", "label": "townstead.variant.colour", "any": true,
 *             "values": [{"id": "white", "label": "color.minecraft.white", "icon": "minecraft:white_dye"}]}],
 *   "families": [{"id": "townstead:wool", "base": "minecraft:wool", "label": "block.minecraft.white_wool",
 *                 "icon": "minecraft:white_wool", "axes": ["townstead:colour"], "items": "minecraft:{value}_wool"}]
 * }
 * }</pre>
 *
 * <p>A family of product keys rather than items gives a {@code "products"} template instead of a base and members:
 * {@code "malrem_product:prepared/{malrem:method}/{base.namespace}/{base.path}"}. Every key the template spells is a
 * variant of the item it names as the base.</p>
 *
 * <p>A value may say how a template spells it ({@code "spelled": ""} for a plain potion's missing prefix), and
 * {@code "only_when_offered": true} keeps it off the sheet where nothing offers it, instead of greyed.</p>
 *
 * <p>A family names its members by {@code "items"}, a pattern over its one axis ({@code {value}}), or by
 * {@code "members"}: {@code [{"values": {"townstead:colour": "red"}, "item": "minecraft:red_wool"}]}. Members whose item
 * is not in the game are left out, so a family may name another mod's items. Axes from every file are shared.</p>
 */
public final class VariantDefs {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Variants");
    public static final String SCHEMA = "townstead:variants/v1";
    private static final String FOLDER = "townstead/variants";

    private VariantDefs() {}

    /** Reads the given files into axes and families; malformed entries are skipped with a warning. */
    static void load(Map<ResourceLocation, JsonObject> files) {
        Map<ResourceLocation, VariantAxis> axes = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : files.entrySet()) {
            for (JsonElement el : GsonHelper.getAsJsonArray(e.getValue(), "axes", new com.google.gson.JsonArray())) {
                VariantAxis axis = parseAxis(el, e.getKey());
                if (axis != null) axes.put(axis.id(), axis);
            }
        }
        List<VariantFamily> families = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : files.entrySet()) {
            for (JsonElement el : GsonHelper.getAsJsonArray(e.getValue(), "families", new com.google.gson.JsonArray())) {
                VariantFamily family = parseFamily(el, axes, e.getKey());
                if (family != null) families.add(family);
            }
        }
        ProductVariants.replaceData(families, axes);
        if (!families.isEmpty()) LOGGER.info("Loaded {} variant axes and {} families", axes.size(), families.size());
    }

    private static @Nullable VariantAxis parseAxis(JsonElement el, ResourceLocation file) {
        if (!el.isJsonObject()) return warn(file, "an axis is not an object");
        JsonObject o = el.getAsJsonObject();
        ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(o, "id", ""));
        if (id == null) return warn(file, "an axis has no id");
        List<VariantAxis.Value> values = new ArrayList<>();
        for (JsonElement v : GsonHelper.getAsJsonArray(o, "values", new com.google.gson.JsonArray())) {
            if (!v.isJsonObject()) continue;
            JsonObject vo = v.getAsJsonObject();
            String vid = GsonHelper.getAsString(vo, "id", "");
            if (!VariantAxis.validValueId(vid)) {
                warn(file, "axis " + id + " has a value id that cannot be used: '" + vid + "'");
                continue;
            }
            ResourceLocation icon = vo.has("icon") ? ResourceLocation.tryParse(GsonHelper.getAsString(vo, "icon", "")) : null;
            values.add(new VariantAxis.Value(vid, GsonHelper.getAsString(vo, "label", vid), icon,
                    GsonHelper.getAsString(vo, "spelled", vid), GsonHelper.getAsBoolean(vo, "only_when_offered", false)));
        }
        if (values.isEmpty()) return warn(file, "axis " + id + " has no values");
        return new VariantAxis(id, GsonHelper.getAsString(o, "label", id.toString()), GsonHelper.getAsBoolean(o, "any", false), values);
    }

    private static @Nullable VariantFamily parseFamily(JsonElement el, Map<ResourceLocation, VariantAxis> known, ResourceLocation file) {
        if (!el.isJsonObject()) return warn(file, "a family is not an object");
        JsonObject o = el.getAsJsonObject();
        ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(o, "id", ""));
        if (id == null || id.getPath().contains("/")) return warn(file, "a family needs an id without '/'");
        List<VariantAxis> axes = new ArrayList<>();
        for (JsonElement a : GsonHelper.getAsJsonArray(o, "axes", new com.google.gson.JsonArray())) {
            VariantAxis axis = known.get(ResourceLocation.tryParse(a.getAsString()));
            if (axis == null) return warn(file, "family " + id + " names an axis no file declares: " + a);
            axes.add(axis);
        }
        if (axes.isEmpty()) return warn(file, "family " + id + " has no axes");
        if (o.has("products")) {
            VariantFamily template = TemplateVariantFamily.of(id, GsonHelper.getAsString(o, "products", ""),
                    o.has("label") ? GsonHelper.getAsString(o, "label", "") : null,
                    o.has("icon") ? GsonHelper.getAsString(o, "icon", "") : null, axes);
            if (template == null) {
                return warn(file, "family " + id + ": \"products\" needs {base.namespace}, {base.path} and each axis once");
            }
            return template;
        }
        ResourceLocation base = ResourceLocation.tryParse(GsonHelper.getAsString(o, "base", ""));
        if (base == null) return warn(file, "family " + id + " needs a base or a \"products\" template");
        List<ItemVariantFamily.Member> members = new ArrayList<>();
        if (o.has("items")) {
            if (axes.size() != 1) return warn(file, "family " + id + ": an \"items\" pattern needs exactly one axis");
            String pattern = GsonHelper.getAsString(o, "items", "");
            for (VariantAxis.Value v : axes.get(0).values()) {
                ResourceLocation item = ResourceLocation.tryParse(pattern.replace("{value}", v.id()));
                if (present(item)) members.add(new ItemVariantFamily.Member(Map.of(axes.get(0).id(), v.id()), item));
            }
        }
        for (JsonElement m : GsonHelper.getAsJsonArray(o, "members", new com.google.gson.JsonArray())) {
            if (!m.isJsonObject()) continue;
            JsonObject mo = m.getAsJsonObject();
            ResourceLocation item = ResourceLocation.tryParse(GsonHelper.getAsString(mo, "item", ""));
            if (!present(item)) continue;
            Map<ResourceLocation, String> values = new LinkedHashMap<>();
            JsonObject vo = GsonHelper.getAsJsonObject(mo, "values", new JsonObject());
            for (VariantAxis axis : axes) {
                String v = GsonHelper.getAsString(vo, axis.id().toString(), "");
                if (axis.value(v) == null) {
                    values = null;
                    break;
                }
                values.put(axis.id(), v);
            }
            if (values == null) {
                warn(file, "family " + id + ": member " + item + " does not give a known value for every axis");
                continue;
            }
            members.add(new ItemVariantFamily.Member(values, item));
        }
        if (members.isEmpty()) return null;
        ResourceLocation icon = o.has("icon") ? ResourceLocation.tryParse(GsonHelper.getAsString(o, "icon", "")) : members.get(0).item();
        return new ItemVariantFamily(id, base, GsonHelper.getAsString(o, "label", base.toString()), icon, axes, members);
    }

    private static boolean present(@Nullable ResourceLocation item) {
        return item != null && BuiltInRegistries.ITEM.containsKey(item);
    }

    private static <T> @Nullable T warn(ResourceLocation file, String message) {
        LOGGER.warn("Variants {}: {}", file, message);
        return null;
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {

        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : resourceManager
                    .listResources(FOLDER, loc -> loc.getPath().endsWith(".json")).entrySet()) {
                try (Reader reader = e.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) continue;
                    String schema = GsonHelper.getAsString(parsed.getAsJsonObject(), "schema", SCHEMA);
                    if (!SCHEMA.equals(schema)) {
                        LOGGER.warn("Variants {}: schema {} is not {}", e.getKey(), schema, SCHEMA);
                        continue;
                    }
                    out.put(e.getKey(), parsed.getAsJsonObject());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read variants {}: {}", e.getKey(), ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
            load(prepared);
        }
    }
}
