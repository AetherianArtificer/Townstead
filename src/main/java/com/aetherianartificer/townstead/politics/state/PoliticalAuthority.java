package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.politics.definition.OrganizationRoleDefinition;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Actor-scoped capability resolution shared by Townstead commands and add-on API reads. */
public final class PoliticalAuthority {
    private PoliticalAuthority() {}

    public static Decision mayAct(PoliticalSavedData data, UUID person, PoliticalActorRef actor,
                                  ResourceLocation capability) {
        if (data == null || person == null || actor == null || capability == null) {
            return deny("invalid_request");
        }

        if (actor.kind() == PoliticalActorRef.Kind.POLITY && data.externalGovernment(actor.id())
                || actor.kind() == PoliticalActorRef.Kind.ORGANIZATION && data.supersededGovernment(actor.id())) return deny("external_authority");
        ResourceLocation organizationId;
        if (actor.kind() == PoliticalActorRef.Kind.ORGANIZATION) {
            OrganizationInstance organization = data.organization(actor.id());
            if (organization == null) return deny("unknown_actor");
            if (organization.status() != PoliticalStatus.Organization.ACTIVE) return deny("inactive_actor");
            organizationId = organization.id();
        } else {
            PolityInstance polity = data.polity(actor.id());
            if (polity == null) return deny("unknown_actor");
            if (polity.status() != PoliticalStatus.Polity.ACTIVE) return deny("inactive_actor");
            if (polity.governmentOrganization() == null) return deny("no_government");
            organizationId = polity.governmentOrganization();
            OrganizationInstance government = data.organization(organizationId);
            if (government == null) return deny("no_government");
            if (government.status() != PoliticalStatus.Organization.ACTIVE) return deny("inactive_government");
        }

        MembershipInstance membership = data.membership(person, organizationId);
        if (membership == null) return deny("not_affiliated");
        if (!membership.affiliation().active()) return deny("inactive_membership");

        PoliticalDefinitions.Snapshot definitions = PoliticalDefinitions.snapshot();
        for (ResourceLocation roleId : membership.roles()) {
            OrganizationRoleDefinition role = definitions.role(roleId);
            if (role != null && role.capabilities().contains(capability)) {
                return new Decision(true, reason("allowed"), roleId);
            }
        }
        return deny("missing_capability");
    }

    private static Decision deny(String reason) {
        return new Decision(false, reason(reason), null);
    }

    private static ResourceLocation reason(String path) {
        ResourceLocation id = DataPackLang.parseId("townstead:" + path);
        if (id == null) throw new IllegalStateException(path);
        return id;
    }

    public record Decision(boolean allowed, ResourceLocation reason, @Nullable ResourceLocation grantingRole) {}
}
