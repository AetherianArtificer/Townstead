package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Admission and departure rules shared by organization kinds and instances. */
public record MembershipPolicyDefinition(ResourceLocation id,
                                         Admission admission,
                                         Departure departure) {
    public static final String SCHEMA = "townstead:membership_policy/v1";

    public static MembershipPolicyDefinition parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        return new MembershipPolicyDefinition(id,
                Admission.parse(PoliticalJson.object(json, "admission", true)),
                Departure.parse(PoliticalJson.object(json, "departure", true)));
    }

    public Set<ResourceLocation> referencedRoles() {
        LinkedHashSet<ResourceLocation> roles = new LinkedHashSet<>(admission.initialRoles());
        if (admission.decision() != null && admission.decision().role() != null) {
            roles.add(admission.decision().role());
        }
        return Set.copyOf(roles);
    }

    public record Admission(ResourceLocation procedure,
                            @Nullable ResourceLocation requestKind,
                            Condition eligibility,
                            List<ResourceLocation> initialRoles,
                            @Nullable Decision decision) {
        public Admission {
            initialRoles = List.copyOf(initialRoles);
        }

        static Admission parse(JsonObject json) {
            Condition eligibility = Conditions.ALWAYS;
            if (json.has("eligibility")) {
                eligibility = Conditions.parse(json.get("eligibility"));
                if (eligibility == null) {
                    throw new IllegalArgumentException("'admission.eligibility' is not a registered Pheno condition");
                }
            }
            JsonObject decisionJson = PoliticalJson.object(json, "decision", false);
            return new Admission(PoliticalJson.requiredId(json, "procedure"),
                    PoliticalJson.optionalId(json, "request_kind"), eligibility,
                    PoliticalJson.ids(json, "initial_roles"),
                    decisionJson == null ? null : Decision.parse(decisionJson));
        }
    }

    public record Decision(ResourceLocation procedure,
                           @Nullable ResourceLocation role,
                           int approvals) {
        static Decision parse(JsonObject json) {
            int approvals = GsonHelper.getAsInt(json, "approvals", 1);
            if (approvals < 1) throw new IllegalArgumentException("'decision.approvals' must be at least 1");
            return new Decision(PoliticalJson.requiredId(json, "procedure"),
                    PoliticalJson.optionalId(json, "role"), approvals);
        }
    }

    public record Departure(ResourceLocation procedure,
                            @Nullable ResourceLocation requestKind,
                            int noticeDays) {
        static Departure parse(JsonObject json) {
            int noticeDays = GsonHelper.getAsInt(json, "notice_days", 0);
            if (noticeDays < 0) throw new IllegalArgumentException("'departure.notice_days' cannot be negative");
            return new Departure(PoliticalJson.requiredId(json, "procedure"),
                    PoliticalJson.optionalId(json, "request_kind"), noticeDays);
        }
    }
}
