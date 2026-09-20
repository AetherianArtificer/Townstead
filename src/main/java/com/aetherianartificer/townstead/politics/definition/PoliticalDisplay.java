package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Player-facing presentation shared by political definitions. */
public record PoliticalDisplay(Component name,
                               @Nullable Component description,
                               @Nullable ResourceLocation icon) {

    static PoliticalDisplay parse(JsonObject document, ResourceLocation id, Map<String, String> lang) {
        JsonObject display = GsonHelper.getAsJsonObject(document, "display", new JsonObject());
        Component name = display.has("name")
                ? DataPackLang.parseComponent(display.get("name"), id.getPath(), lang)
                : Component.literal(id.getPath());
        Component description = display.has("description")
                ? DataPackLang.parseComponent(display.get("description"), "", lang)
                : null;
        ResourceLocation icon = null;
        if (display.has("icon")) {
            icon = PoliticalJson.optionalId(display, "icon");
            if (icon == null) throw new IllegalArgumentException("'display.icon' must be a resource id");
        }
        return new PoliticalDisplay(name, description, icon);
    }
}
