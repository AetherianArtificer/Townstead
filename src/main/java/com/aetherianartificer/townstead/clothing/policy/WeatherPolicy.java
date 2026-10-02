package com.aetherianartificer.townstead.clothing.policy;

import com.aetherianartificer.townstead.clothing.ClothingChannel;
import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.clothing.Weather;
import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * "Dress for the weather": the policy every villager has when nothing else says otherwise.
 *
 * <p>Three built-in village policies, one per outdoor reading, at the lowest possible priority
 * so any data or screen policy beats them layer by layer. Cold wants a warm base, warm body
 * outerwear, and a warm hat; hot wants a cool base, no warm outerwear, and a cool hat; a mild
 * day only sheds warm outerwear. The reading is the outdoors, so a coat is not stowed just
 * because the villager stepped into a warm kitchen; see the doff-and-don rule for that.</p>
 */
public final class WeatherPolicy {

    public static final String NAMESPACE = "townstead";
    public static final int PRIORITY = Integer.MIN_VALUE;

    private static final ClothingQuery WARM = new ClothingQuery(null, null, Set.of(), Set.of(), Set.of(),
            ClothingQuery.Thermal.WARM, Set.of());
    private static final ClothingQuery COOL = new ClothingQuery(null, null, Set.of(), Set.of(), Set.of(),
            ClothingQuery.Thermal.COOL, Set.of());
    private static final ClothingQuery WARM_BODY = new ClothingQuery(null, ClothingChannel.BODY, Set.of(), Set.of(),
            Set.of(), ClothingQuery.Thermal.WARM, Set.of());
    private static final ClothingQuery WARM_HEAD = new ClothingQuery(null, ClothingChannel.HEAD, Set.of(), Set.of(),
            Set.of(), ClothingQuery.Thermal.WARM, Set.of());
    private static final ClothingQuery COOL_HEAD = new ClothingQuery(null, ClothingChannel.HEAD, Set.of(), Set.of(),
            Set.of(), ClothingQuery.Thermal.COOL, Set.of());

    private static final WardrobePolicy COLD = build("cold", Map.of(
            ClothingLayer.BASE, rule(WardrobePolicy.Requirement.REQUIRED, WARM),
            ClothingLayer.OUTERWEAR, rule(WardrobePolicy.Requirement.REQUIRED, WARM_BODY),
            ClothingLayer.ACCESSORY, rule(WardrobePolicy.Requirement.PREFERRED, WARM_HEAD)));
    private static final WardrobePolicy MILD = build("mild", Map.of(
            ClothingLayer.OUTERWEAR, rule(WardrobePolicy.Requirement.NONE, WARM)));
    private static final WardrobePolicy HOT = build("hot", Map.of(
            ClothingLayer.BASE, rule(WardrobePolicy.Requirement.REQUIRED, COOL),
            ClothingLayer.OUTERWEAR, rule(WardrobePolicy.Requirement.NONE, WARM),
            ClothingLayer.ACCESSORY, rule(WardrobePolicy.Requirement.PREFERRED, COOL_HEAD)));

    private WeatherPolicy() {}

    public static WardrobePolicy of(Weather.Kind kind) {
        if (kind == null) return MILD;
        switch (kind) {
            case COLD: return COLD;
            case HOT: return HOT;
            default: return MILD;
        }
    }

    /** Whether a policy is one of the three weather defaults rather than data. */
    public static boolean isWeather(WardrobePolicy policy) {
        return policy == COLD || policy == MILD || policy == HOT;
    }

    private static WardrobePolicy.LayerRule rule(WardrobePolicy.Requirement requirement, ClothingQuery query) {
        return new WardrobePolicy.LayerRule(requirement, new WardrobePolicy.Selector(null, false, false, null, query));
    }

    private static WardrobePolicy build(String name, Map<ClothingLayer, WardrobePolicy.LayerRule> layers) {
        ResourceLocation id = DataPackLang.parseId(NAMESPACE + ":weather/" + name);
        return new WardrobePolicy(id, WardrobePolicy.Scope.VILLAGE, Set.of(), PRIORITY, null,
                new EnumMap<>(layers));
    }
}
