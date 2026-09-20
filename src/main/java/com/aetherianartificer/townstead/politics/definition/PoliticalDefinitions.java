package com.aetherianartificer.townstead.politics.definition;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic, immutable view of all loaded political definition families. */
public final class PoliticalDefinitions {
    private static volatile Snapshot SNAPSHOT = Snapshot.EMPTY;

    private PoliticalDefinitions() {}

    public static Snapshot snapshot() {
        return SNAPSHOT;
    }

    static void replace(Map<ResourceLocation, OrganizationRoleDefinition> roles,
                        Map<ResourceLocation, MembershipPolicyDefinition> policies,
                        Map<ResourceLocation, OrganizationKindDefinition> kinds) {
        SNAPSHOT = new Snapshot(roles, policies, kinds);
    }

    /** Returns every unresolved cross-document reference; an empty list is a coherent pack. */
    public static List<String> validate(Map<ResourceLocation, OrganizationRoleDefinition> roles,
                                        Map<ResourceLocation, MembershipPolicyDefinition> policies,
                                        Map<ResourceLocation, OrganizationKindDefinition> kinds) {
        List<String> errors = new ArrayList<>();
        for (MembershipPolicyDefinition policy : policies.values()) {
            for (ResourceLocation role : policy.referencedRoles()) {
                if (!roles.containsKey(role)) errors.add("Membership policy " + policy.id()
                        + " references unknown role " + role);
            }
        }
        for (OrganizationKindDefinition kind : kinds.values()) {
            if (!policies.containsKey(kind.membershipPolicy())) {
                errors.add("Organization kind " + kind.id() + " references unknown membership policy "
                        + kind.membershipPolicy());
            }
            for (OrganizationKindDefinition.RoleBinding binding : kind.roles()) {
                if (!roles.containsKey(binding.role())) errors.add("Organization kind " + kind.id()
                        + " references unknown role " + binding.role());
            }
        }
        return List.copyOf(errors);
    }

    public record Snapshot(Map<ResourceLocation, OrganizationRoleDefinition> roles,
                           Map<ResourceLocation, MembershipPolicyDefinition> membershipPolicies,
                           Map<ResourceLocation, OrganizationKindDefinition> organizationKinds) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of());

        public Snapshot {
            roles = Map.copyOf(new LinkedHashMap<>(roles));
            membershipPolicies = Map.copyOf(new LinkedHashMap<>(membershipPolicies));
            organizationKinds = Map.copyOf(new LinkedHashMap<>(organizationKinds));
        }

        public @Nullable OrganizationRoleDefinition role(ResourceLocation id) {
            return roles.get(id);
        }

        public @Nullable MembershipPolicyDefinition membershipPolicy(ResourceLocation id) {
            return membershipPolicies.get(id);
        }

        public @Nullable OrganizationKindDefinition organizationKind(ResourceLocation id) {
            return organizationKinds.get(id);
        }

        public Collection<OrganizationKindDefinition> kinds() {
            return organizationKinds.values();
        }
    }
}
