package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.SkinSelection;
import net.conczin.mca.resources.data.skin.Clothing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Resolves a Profession's ordered clothing identities against MCA's merged clothing catalogue. */
public final class ProfessionClothing {

    private ProfessionClothing() {}

    /**
     * Applies presentation policy after MCA has reacted to a Profession change.
     *
     * <p>MCA normally falls back to civilian clothing when the new Profession has no wardrobe.
     * A Townstead Profession without specialized clothing instead keeps the villager's previous
     * clothes. An authored {@code clothing} chain names wardrobe identities in preference order;
     * the first identity with a gender-compatible entry wins. Clothing locks always win.</p>
     */
    public static void afterProfessionChange(VillagerEntityMCA villager, String previousClothes) {
        if (villager == null || villager.level().isClientSide || villager.isClothingLocked()) return;
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        if (professionId == null) return;

        // Presentation is owned only by the Profession's own document. Semantic aliases must not
        // make one mod's Career silently replace another mod's native clothing behavior.
        ProfessionDef def = ProfessionDefs.all().get(professionId);
        if (def == null) return;

        Collection<Clothing> catalogue = catalogue();
        if (def.clothing().isEmpty()) {
            // A direct MCA wardrobe is already specialized and MCA just selected from it. Only
            // undo MCA's generic civilian fallback when the custom Profession has no wardrobe.
            if (compatible(catalogue, villager, professionId).isEmpty()) {
                villager.setClothes(previousClothes == null ? "" : previousClothes);
            }
            return;
        }

        for (ResourceLocation source : def.clothing()) {
            List<Clothing> options = compatible(catalogue, villager, source);
            if (options.isEmpty()) continue;
            Optional<String> selected = SkinSelection.pickWeightedId(options);
            if (selected.isPresent()) {
                villager.setClothes(selected.get());
                return;
            }
        }
        villager.setClothes(previousClothes == null ? "" : previousClothes);
    }

    static Collection<Clothing> catalogue() {
        ClothingList list = ClothingList.getInstance();
        return list == null ? List.of() : list.clothing.values();
    }

    static List<Clothing> compatible(Collection<Clothing> catalogue,
                                     VillagerEntityMCA villager,
                                     ResourceLocation source) {
        return compatible(catalogue, villager.getGenetics().getGender(), source);
    }

    /**
     * Explicit matches for one entry in an ordered clothing chain. MCA's ordinary Profession
     * helper deliberately mixes generic civilian clothes into every Profession pool; using it
     * here made an absent custom wardrobe look available and could let civilian clothing beat a
     * later authored fallback. A Townstead chain entry means that workwear identity specifically.
     */
    static List<Clothing> compatible(Collection<Clothing> catalogue,
                                     Gender gender,
                                     ResourceLocation source) {
        String namespaced = source.toString();
        String dotted = namespaced.replace(':', '.');
        return catalogue.stream()
                .filter(option -> SkinSelection.matchesGender(option, gender))
                .filter(option -> namespaced.equals(option.profession) || dotted.equals(option.profession))
                .sorted((left, right) -> left.getIdentifier().compareTo(right.getIdentifier()))
                .toList();
    }

    /** First renderable identity in an authored chain, retaining a valid current variant. */
    static Optional<String> firstAvailable(Collection<Clothing> catalogue,
                                           Gender gender,
                                           List<ResourceLocation> sources,
                                           String current,
                                           Predicate<String> available) {
        for (ResourceLocation source : sources) {
            if (source == null) continue;
            List<Clothing> options = compatible(catalogue, gender, source);
            if (options.stream().map(Clothing::getIdentifier)
                    .anyMatch(id -> Objects.equals(id, current) && available.test(id))) {
                return Optional.of(current);
            }
            Optional<String> selected = SkinSelection.pickWeightedId(options.stream()
                    .filter(option -> available.test(option.getIdentifier()))
                    .toList());
            if (selected.isPresent()) return selected;
        }
        return Optional.empty();
    }
}
