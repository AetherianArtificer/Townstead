package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.ModGate;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads roles, policies, and kinds together so a reload publishes one coherent snapshot. */
public final class PoliticalDefinitionLoader
        extends SimplePreparableReloadListener<PoliticalDefinitionLoader.Prepared> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/PoliticalDefinitions");

    public record Prepared(Map<ResourceLocation, JsonObject> roles,
                           Map<ResourceLocation, JsonObject> policies,
                           Map<ResourceLocation, JsonObject> kinds) {}

    @Override
    protected Prepared prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return new Prepared(read(resourceManager, "organization_role"),
                read(resourceManager, "membership_policy"),
                read(resourceManager, "organization_kind"));
    }

    private static Map<ResourceLocation, JsonObject> read(ResourceManager manager, String directory) {
        Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
        String prefix = directory + "/";
        for (Map.Entry<ResourceLocation, Resource> entry : manager
                .listResources(directory, id -> id.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            String path = file.getPath();
            ResourceLocation id = DataPackLang.parseId(file.getNamespace() + ":"
                    + path.substring(prefix.length(), path.length() - ".json".length()));
            if (id == null) continue;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                else LOGGER.warn("Political definition {} is not a JSON object", file);
            } catch (Exception error) {
                LOGGER.warn("Could not read political definition {}: {}", file, error.getMessage());
            }
        }
        return out;
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, OrganizationRoleDefinition> roles = parseRoles(prepared.roles(), lang);
        Map<ResourceLocation, MembershipPolicyDefinition> policies = parsePolicies(prepared.policies());
        Map<ResourceLocation, OrganizationKindDefinition> kinds = parseKinds(prepared.kinds(), lang);

        // A broken dependency invalidates its direct owner, not the entire political pack.
        policies.entrySet().removeIf(entry -> {
            for (ResourceLocation role : entry.getValue().referencedRoles()) {
                if (!roles.containsKey(role)) {
                    LOGGER.warn("Membership policy {} rejected: unknown role {}", entry.getKey(), role);
                    return true;
                }
            }
            return false;
        });
        kinds.entrySet().removeIf(entry -> {
            OrganizationKindDefinition kind = entry.getValue();
            if (!policies.containsKey(kind.membershipPolicy())) {
                LOGGER.warn("Organization kind {} rejected: unknown membership policy {}",
                        entry.getKey(), kind.membershipPolicy());
                return true;
            }
            for (OrganizationKindDefinition.RoleBinding binding : kind.roles()) {
                if (!roles.containsKey(binding.role())) {
                    LOGGER.warn("Organization kind {} rejected: unknown role {}", entry.getKey(), binding.role());
                    return true;
                }
            }
            return false;
        });

        PoliticalDefinitions.replace(roles, policies, kinds);
        LOGGER.info("Loaded {} organization roles, {} membership policies, and {} organization kinds",
                roles.size(), policies.size(), kinds.size());
    }

    private static Map<ResourceLocation, OrganizationRoleDefinition> parseRoles(
            Map<ResourceLocation, JsonObject> raw, Map<String, String> lang) {
        Map<ResourceLocation, OrganizationRoleDefinition> out = new LinkedHashMap<>();
        raw.forEach((id, json) -> {
            if (!enabled(id, json)) return;
            try {
                out.put(id, OrganizationRoleDefinition.parse(id, json, lang));
            } catch (RuntimeException error) {
                LOGGER.warn("Organization role {} rejected: {}", id, error.getMessage());
            }
        });
        return out;
    }

    private static Map<ResourceLocation, MembershipPolicyDefinition> parsePolicies(
            Map<ResourceLocation, JsonObject> raw) {
        Map<ResourceLocation, MembershipPolicyDefinition> out = new LinkedHashMap<>();
        raw.forEach((id, json) -> {
            if (!enabled(id, json)) return;
            try {
                out.put(id, MembershipPolicyDefinition.parse(id, json));
            } catch (RuntimeException error) {
                LOGGER.warn("Membership policy {} rejected: {}", id, error.getMessage());
            }
        });
        return out;
    }

    private static Map<ResourceLocation, OrganizationKindDefinition> parseKinds(
            Map<ResourceLocation, JsonObject> raw, Map<String, String> lang) {
        Map<ResourceLocation, OrganizationKindDefinition> out = new LinkedHashMap<>();
        raw.forEach((id, json) -> {
            if (!enabled(id, json)) return;
            try {
                out.put(id, OrganizationKindDefinition.parse(id, json, lang));
            } catch (RuntimeException error) {
                LOGGER.warn("Organization kind {} rejected: {}", id, error.getMessage());
            }
        });
        return out;
    }

    private static boolean enabled(ResourceLocation id, JsonObject json) {
        if (!json.has("mods")) return true;
        Boolean enabled = ModGate.evaluate(json.get("mods"));
        if (enabled == null) LOGGER.warn("Political definition {} rejected: malformed 'mods' gate", id);
        else if (!enabled) LOGGER.debug("Political definition {} skipped: required mods are unavailable", id);
        return Boolean.TRUE.equals(enabled);
    }
}
