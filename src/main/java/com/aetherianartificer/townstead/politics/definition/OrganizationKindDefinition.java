package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Data-authored blueprint for persistent organization instances. */
public record OrganizationKindDefinition(ResourceLocation id,
                                         PoliticalDisplay display,
                                         Set<ResourceLocation> tags,
                                         ResourceLocation membershipPolicy,
                                         List<RoleBinding> roles,
                                         Founding founding,
                                         Presentation presentation,
                                         @Nullable GovernanceDefinition governance) {
    public static final String SCHEMA = "townstead:organization_kind/v1";

    public OrganizationKindDefinition {
        tags = Set.copyOf(tags);
        roles = List.copyOf(roles);
    }

    public OrganizationKindDefinition(ResourceLocation id, PoliticalDisplay display, Set<ResourceLocation> tags,
                                      ResourceLocation membershipPolicy, List<RoleBinding> roles,
                                      Founding founding, Presentation presentation) {
        this(id, display, tags, membershipPolicy, roles, founding, presentation, null);
    }

    public static OrganizationKindDefinition parse(ResourceLocation id, JsonObject json,
                                                   Map<String, String> lang) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        JsonArray bindings = PoliticalJson.array(json, "roles", true);
        if (bindings.isEmpty()) throw new IllegalArgumentException("'roles' cannot be empty");
        List<RoleBinding> roles = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (JsonElement element : bindings) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Every role binding must be an object");
            RoleBinding binding = RoleBinding.parse(element.getAsJsonObject());
            if (!seen.add(binding.role())) throw new IllegalArgumentException("Role " + binding.role() + " is bound twice");
            roles.add(binding);
        }
        JsonObject foundingJson = PoliticalJson.object(json, "founding", true);
        JsonObject presentationJson = PoliticalJson.object(json, "presentation", false);
        JsonObject governanceJson = PoliticalJson.object(json, "governance", false);
        return new OrganizationKindDefinition(id, PoliticalDisplay.parse(json, id, lang),
                PoliticalJson.idSet(json, "tags"), PoliticalJson.requiredId(json, "membership_policy"),
                roles, Founding.parse(foundingJson), Presentation.parse(presentationJson),
                governanceJson == null ? null : GovernanceDefinition.parse(governanceJson, seen));
    }

    public record RoleBinding(ResourceLocation role, int minimum, int maximum, boolean founder) {
        static RoleBinding parse(JsonObject json) {
            int minimum = GsonHelper.getAsInt(json, "min", 0);
            int maximum = GsonHelper.getAsInt(json, "max", -1);
            if (minimum < 0) throw new IllegalArgumentException("Role minimum cannot be negative");
            if (maximum < -1 || (maximum >= 0 && maximum < minimum)) {
                throw new IllegalArgumentException("Role maximum must be -1 or at least its minimum");
            }
            if (GsonHelper.getAsBoolean(json, "founder", false) && maximum == 0) {
                throw new IllegalArgumentException("A founder role cannot have a maximum of zero");
            }
            return new RoleBinding(PoliticalJson.requiredId(json, "role"), minimum, maximum,
                    GsonHelper.getAsBoolean(json, "founder", false));
        }
    }

    public record Founding(ResourceLocation procedure, Condition eligibility) {
        static Founding parse(JsonObject json) {
            Condition eligibility = Conditions.ALWAYS;
            if (json.has("eligibility")) {
                eligibility = Conditions.parse(json.get("eligibility"));
                if (eligibility == null) {
                    throw new IllegalArgumentException("'founding.eligibility' is not a registered Pheno condition");
                }
            }
            return new Founding(PoliticalJson.requiredId(json, "procedure"), eligibility);
        }
    }

    public record Presentation(@Nullable ResourceLocation emblemPool,
                               @Nullable ResourceLocation charterStyle, List<String> factionNamePatterns) {
        public Presentation(ResourceLocation emblemPool, ResourceLocation charterStyle) {
            this(emblemPool, charterStyle, List.of("{name}"));
        }
        public Presentation { factionNamePatterns = List.copyOf(factionNamePatterns); }
        static Presentation parse(@Nullable JsonObject json) {
            return json == null ? new Presentation(null, null)
                    : new Presentation(PoliticalJson.optionalId(json, "emblem_pool"),
                    PoliticalJson.optionalId(json, "charter_style"),
                    com.aetherianartificer.townstead.culture.FactionNaming.parsePatterns(json));
        }
    }
}
