package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * How a government organization kind is led: its head office, the routes by which people take
 * offices, what happens when the head's office is empty, and what its legitimacy rests on. The
 * {@code governance} block of an {@code organization_kind}; routes name registered mechanics.
 */
public record GovernanceDefinition(ResourceLocation head,
                                   List<Route> routes,
                                   ResourceLocation succession,
                                   Legitimacy legitimacy) {
    public GovernanceDefinition {
        routes = List.copyOf(routes);
    }

    /**
     * One way to take one office. {@code settings} holds the route's own numbers, which the route
     * reads; unknown keys are the route's concern.
     */
    public record Route(ResourceLocation route, ResourceLocation office, Condition eligibility, JsonObject settings) {}

    /** A starting value for rulers who took power peacefully, and what raises or lowers it. */
    public record Legitimacy(int base, List<Source> sources) {
        public Legitimacy {
            sources = List.copyOf(sources);
        }
    }

    /** One thing legitimacy rests on, such as victories or shared food; {@code label} names it on the Charter. */
    public record Source(ResourceLocation label, Value value, double weight) {}

    static GovernanceDefinition parse(JsonObject json, Set<ResourceLocation> roles) {
        ResourceLocation head = PoliticalJson.requiredId(json, "head");
        if (!roles.contains(head)) throw new IllegalArgumentException("'governance.head' " + head + " is not a role of this kind");
        List<Route> routes = new ArrayList<>();
        for (JsonElement element : arrayOrEmpty(json, "routes")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Every governance route must be an object");
            JsonObject routeJson = element.getAsJsonObject();
            ResourceLocation route = knownRoute(PoliticalJson.requiredId(routeJson, "route"));
            ResourceLocation office = PoliticalJson.requiredId(routeJson, "office");
            if (!roles.contains(office)) throw new IllegalArgumentException("Route office " + office + " is not a role of this kind");
            Condition eligibility = Conditions.ALWAYS;
            if (routeJson.has("eligibility")) {
                eligibility = Conditions.parse(routeJson.get("eligibility"));
                if (eligibility == null) throw new IllegalArgumentException("Route eligibility is not a registered Pheno condition");
            }
            JsonObject settings = PoliticalJson.object(routeJson, "settings", false);
            routes.add(new Route(route, office, eligibility, settings == null ? new JsonObject() : settings.deepCopy()));
        }
        ResourceLocation succession = knownRoute(PoliticalJson.requiredId(json, "succession"));
        return new GovernanceDefinition(head, routes, succession, parseLegitimacy(PoliticalJson.object(json, "legitimacy", false)));
    }

    private static Legitimacy parseLegitimacy(@Nullable JsonObject json) {
        if (json == null) return new Legitimacy(GovernanceRoutes.DEFAULT_LEGITIMACY, List.of());
        int base = GsonHelper.getAsInt(json, "base", GovernanceRoutes.DEFAULT_LEGITIMACY);
        if (base < 0 || base > 100) throw new IllegalArgumentException("'legitimacy.base' must be between 0 and 100");
        List<Source> sources = new ArrayList<>();
        for (JsonElement element : arrayOrEmpty(json, "sources")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Every legitimacy source must be an object");
            JsonObject source = element.getAsJsonObject();
            Value value = Values.parse(source.get("value"));
            if (value == null) throw new IllegalArgumentException("Legitimacy source value is not a registered Pheno value");
            sources.add(new Source(PoliticalJson.requiredId(source, "label"), value, GsonHelper.getAsDouble(source, "weight", 1.0)));
        }
        return new Legitimacy(base, sources);
    }

    private static com.google.gson.JsonArray arrayOrEmpty(JsonObject json, String field) {
        com.google.gson.JsonArray array = PoliticalJson.array(json, field, false);
        return array == null ? new com.google.gson.JsonArray() : array;
    }

    private static ResourceLocation knownRoute(ResourceLocation id) {
        if (!GovernanceRoutes.isKnown(id)) {
            throw new IllegalArgumentException("Unknown governance route " + id + "; known routes are " + GovernanceRoutes.known());
        }
        return id;
    }
}
