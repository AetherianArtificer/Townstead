package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.resources.WeightedPool;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Reading a weighted bag of names out of JSON, shared by everything that holds one. */
public final class NamePools {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/NamePools");

    private NamePools() {}

    /**
     * One weighted pool, or null when the key is absent or holds nothing usable.
     *
     * <p>A flat array weights every entry equally; an object maps a name to a weight and applies the
     * same square-root curve MCA uses on its own files, so a pool behaves the same whichever mod an
     * author copied it from.</p>
     */
    public static @Nullable WeightedPool<String> pool(JsonObject owner, String key, ResourceLocation file) {
        JsonElement element = owner.get(key);
        if (element == null) return null;

        WeightedPool.Mutable<String> names = new WeightedPool.Mutable<>("?");
        boolean any = false;

        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                if (value == null || !value.isJsonPrimitive()) continue;
                String name = value.getAsString();
                if (name == null || name.isBlank()) continue;
                names.add(name.trim(), 1.0F);
                any = true;
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> weighted : element.getAsJsonObject().entrySet()) {
                String name = weighted.getKey();
                if (name == null || name.isBlank()) continue;
                int weight;
                try {
                    weight = weighted.getValue().getAsInt();
                } catch (Exception ignored) {
                    LOGGER.warn("Names {} gave '{}' a non-numeric weight, ignoring it", file, name);
                    continue;
                }
                if (weight <= 0) continue;
                names.add(name.trim(), (float) Math.pow(weight, 0.5));
                any = true;
            }
        } else {
            LOGGER.warn("Names {} field '{}' must be a list or an object of name to weight", file, key);
            return null;
        }

        return any ? names : null;
    }
}
