package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A reusable organization role. Role labels never grant authority; capabilities do. */
public record OrganizationRoleDefinition(ResourceLocation id,
                                         PoliticalDisplay display,
                                         Set<ResourceLocation> capabilities,
                                         Visibility visibility) {
    public static final String SCHEMA = "townstead:organization_role/v1";

    public OrganizationRoleDefinition {
        capabilities = Set.copyOf(capabilities);
    }

    public static OrganizationRoleDefinition parse(ResourceLocation id, JsonObject json,
                                                   Map<String, String> lang) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        Visibility visibility;
        try {
            visibility = Visibility.valueOf(GsonHelper.getAsString(json, "visibility", "public")
                    .trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("'visibility' must be public, members, officers, or secret");
        }
        return new OrganizationRoleDefinition(id, PoliticalDisplay.parse(json, id, lang),
                PoliticalJson.idSet(json, "capabilities"), visibility);
    }

    public enum Visibility {
        PUBLIC,
        MEMBERS,
        OFFICERS,
        SECRET
    }
}
