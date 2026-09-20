package com.aetherianartificer.townstead.culture;

import net.conczin.mca.resources.WeightedPool;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

/** A reusable, data-authored bag of names for settlements belonging to a culture. */
public record SettlementNamePool(ResourceLocation id,
                                 WeightedPool<Form> forms,
                                 Set<String> values) {
    public SettlementNamePool {
        values = values == null ? Set.of() : Set.copyOf(values);
    }

    public boolean contains(String name) {
        return name != null && values.contains(name.trim());
    }

    public String pick() {
        Form form = forms == null ? null : forms.pickOne();
        return form == null ? "" : form.pick();
    }

    /** One literal or templated name in the weighted outer pool. */
    public record Form(String template, Map<String, WeightedPool<String>> parts) {
        public Form {
            template = template == null ? "" : template;
            parts = parts == null ? Map.of() : Map.copyOf(parts);
        }

        public String pick() {
            String value = template;
            for (Map.Entry<String, WeightedPool<String>> part : parts.entrySet()) {
                value = value.replace("{" + part.getKey() + "}", part.getValue().pickOne());
            }
            return value.trim();
        }
    }
}
