package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ClothingChoice;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.data.skin.Clothing;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Resolves Townstead workwear against clothing textures available to this client. */
public final class ClientProfessionClothing {

    private static final ResourceLocation CIVILIAN = ResourceLocation.tryParse("minecraft:none");
    private static final String IMMERSIVE_LIBRARY_PREFIX = "immersive_library:";

    private ClientProfessionClothing() {}

    /**
     * Ensures that a preview or synced villager never wears a catalogue entry whose texture is
     * absent from this client's active resource packs.
     *
     * <p>A server can load the data half of an optional wardrobe without a client enabling its
     * asset pack. In that case MCA knows the clothing id but renders an empty layer. Townstead
     * walks the authored chain using only locally renderable entries, preserves any unrelated
     * renderable outfit when no chain entry works, and finally chooses MCA civilian clothing.</p>
     */
    public static void ensureRenderable(VillagerEntityMCA villager) {
        if (villager == null || villager.isClothingLocked()) return;

        Collection<Clothing> catalogue = ProfessionClothing.catalogue();
        if (catalogue.isEmpty()) return;

        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        ProfessionDef def = professionId == null ? null : ProfessionDefs.all().get(professionId);

        List<ClothingChoice> choices = ProfessionClothing.choicesFor(villager, def);
        if (!choices.isEmpty() && applyFirstAvailable(villager, catalogue, choices)) {
            return;
        }

        // An unlisted outfit may be the villager's deliberately preserved clothes. Keep it when
        // the client can render it; only a blank, missing, or disabled-pack texture falls through.
        if (textureAvailable(villager.getClothes())) return;

        applyFirstAvailable(villager, catalogue, List.of(new ClothingChoice(CIVILIAN)));
    }

    private static boolean applyFirstAvailable(VillagerEntityMCA villager,
                                               Collection<Clothing> catalogue,
                                               List<ClothingChoice> choices) {
        String current = villager.getClothes();
        // Validation and priority selection are centralized in the common resolver; this client
        // class supplies the local resource-pack predicate.
        Optional<String> selected = ProfessionClothing.firstAvailable(catalogue,
                villager.getGenetics().getGender(), choices, current,
                ClientProfessionClothing::textureAvailable);
        // This runs from MCA's clothing render layer. Re-writing the synced clothing value every
        // frame, even to the identical string, fights MCA's own client updates and presents as a
        // rapid outfit flicker. Only perform the fallback mutation when the value really changes.
        selected.filter(value -> !java.util.Objects.equals(value, current))
                .ifPresent(villager::setClothes);
        return selected.isPresent();
    }

    static boolean textureAvailable(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        if (identifier.startsWith(IMMERSIVE_LIBRARY_PREFIX)) return true;
        ResourceLocation texture = ResourceLocation.tryParse(identifier);
        return texture != null
                && Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
    }
}
