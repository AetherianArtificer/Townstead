package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.SelectorType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Selects the live members of a {@code collection} store on the focus (the same
 * {@code townstead_origins:collection} id, resolved in a selector slot instead of a gene slot).
 * Members that are gone or unloaded are skipped. This is how a stored set feeds {@code on},
 * {@code count}, and the rest of the selection family.
 */
public final class CollectionSelectorType implements SelectorType {

    public static final String KEY = "pheno:collection";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Selector parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "collection", ""));
        if (id == null) return null;
        return ctx -> {
            LivingEntity holder = ctx.self();
            if (holder == null) return List.of();
            List<LivingEntity> out = new ArrayList<>();
            for (String element : CollectionValues.elements(holder, id)) {
                LivingEntity member = CollectionValues.resolveMember(holder, element);
                if (member != null) out.add(member);
            }
            return out;
        };
    }
}
