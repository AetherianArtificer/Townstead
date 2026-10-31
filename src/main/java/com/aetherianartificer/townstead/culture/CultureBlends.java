package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * A person's culture as a blend: the shares of each culture or subculture they carry. The largest
 * share is their recorded culture; the rest color how they speak and dress.
 */
public final class CultureBlends {
    /** A set is claimed by a blend only from a culture holding at least this share. */
    private static final float SET_SHARE = 0.25F;

    private CultureBlends() {}

    /** Each known culture in this villager's blend with its share, largest first. */
    public static Map<Culture, Float> of(VillagerEntityMCA villager) {
        Map<String, Float> raw = TownsteadVillagers.get(villager).life().cultureBlend();
        List<Map.Entry<String, Float>> entries = new ArrayList<>(raw.entrySet());
        entries.sort(Map.Entry.<String, Float>comparingByValue().reversed());
        Map<Culture, Float> out = new LinkedHashMap<>();
        for (Map.Entry<String, Float> e : entries) {
            Culture culture = Cultures.get(e.getKey());
            if (culture != null) out.merge(culture, e.getValue(), Float::sum);
        }
        return out;
    }

    /** One culture drawn from the blend by share, or null when the villager has none. */
    public static @Nullable Culture pick(VillagerEntityMCA villager, RandomGenerator random) {
        Map<Culture, Float> blend = of(villager);
        float total = 0;
        for (float share : blend.values()) total += share;
        if (total <= 0) return null;
        float roll = random.nextFloat() * total;
        for (Map.Entry<Culture, Float> e : blend.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return blend.keySet().iterator().next();
    }

    /** How this villager dresses: every culture's clothing in the blend, each rate weighted by share. */
    public static CultureClothing clothing(VillagerEntityMCA villager) {
        return mix(of(villager));
    }

    /**
     * Mixes clothing by share. Rates multiply, so each bias counts as {@code rate^share}: a culture
     * held wholly keeps its rates, and a trace of one barely moves them. Sets come from cultures with
     * a real share; palette and livery lead with the largest.
     */
    static CultureClothing mix(Map<Culture, Float> blend) {
        if (blend.isEmpty()) return CultureClothing.NONE;
        if (blend.size() == 1) return blend.keySet().iterator().next().clothing();
        List<CultureClothing.SkinBias> skins = new ArrayList<>();
        List<CultureClothing.TypeBias> types = new ArrayList<>();
        Set<ResourceLocation> sets = new LinkedHashSet<>();
        Set<Integer> palette = new LinkedHashSet<>();
        ResourceLocation livery = null;
        for (Map.Entry<Culture, Float> e : blend.entrySet()) {
            CultureClothing clothing = e.getKey().clothing();
            float share = e.getValue();
            for (CultureClothing.SkinBias bias : clothing.skins()) {
                skins.add(new CultureClothing.SkinBias(bias.pattern(), (float) Math.pow(bias.rate(), share), bias.spirits()));
            }
            for (CultureClothing.TypeBias bias : clothing.types()) {
                types.add(new CultureClothing.TypeBias(bias.select(), (float) Math.pow(bias.rate(), share), bias.spirits()));
            }
            if (share >= SET_SHARE) sets.addAll(clothing.sets());
            palette.addAll(clothing.palette());
            if (livery == null) livery = clothing.livery();
        }
        return new CultureClothing(skins, types, List.copyOf(sets), List.copyOf(palette), livery);
    }
}
