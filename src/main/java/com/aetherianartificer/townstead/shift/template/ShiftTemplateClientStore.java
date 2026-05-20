package com.aetherianartificer.townstead.shift.template;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client mirror of the combined template list (built-ins + user). Updated by
 * {@link ShiftTemplateSyncPayload} handler.
 */
public final class ShiftTemplateClientStore {

    private static volatile List<ShiftTemplate> templates = List.of();

    private ShiftTemplateClientStore() {}

    public static void set(List<ShiftTemplate> updated) {
        if (updated == null || updated.isEmpty()) {
            templates = List.of();
            return;
        }
        List<ShiftTemplate> sorted = new ArrayList<>(updated.size());
        for (ShiftTemplate t : updated) if (t != null) sorted.add(t);
        sorted.sort(Comparator
                .<ShiftTemplate>comparingInt(t -> t.builtIn() ? 0 : 1)
                .thenComparing(t -> t.id().toString(), String.CASE_INSENSITIVE_ORDER));
        templates = List.copyOf(sorted);
    }

    public static List<ShiftTemplate> all() {
        return templates;
    }

    public static ShiftTemplate find(ResourceLocation id) {
        if (id == null) return null;
        for (ShiftTemplate t : templates) {
            if (id.equals(t.id())) return t;
        }
        return null;
    }

    public static ShiftTemplate find(String id) {
        if (id == null || id.isEmpty()) return null;
        for (ShiftTemplate t : templates) {
            if (id.equals(t.id().toString())) return t;
        }
        return null;
    }

    public static void clear() {
        templates = List.of();
    }
}
