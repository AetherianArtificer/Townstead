package com.aetherianartificer.townstead.clothing.policy;

import com.aetherianartificer.townstead.clothing.BodyClothingResolver;
import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.Heritage;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.SkinSelection;
import net.conczin.mca.resources.data.skin.Clothing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Work skins for a villager whose rig is not MCA's villager body. MCA picks clothing for its own
 * body by profession; on another body only the skins the root's {@code body_clothing} lists fit, so
 * this makes the same profession-first pick from those: the villager's profession, else civilian
 * clothes, else any fitted skin. The pick is seeded by the villager, so it is stable.
 */
public final class RigSkinPicker {

    private RigSkinPicker() {}

    /** Whether this villager wears a non-MCA body whose root lists fitted skins. */
    public static boolean applies(VillagerEntityMCA villager) {
        return villager != null && !fitted(villager).isEmpty() && !SkinPicker.onMcaRig(rootId(villager));
    }

    /** Whether the skin fits the villager's body. */
    public static boolean fits(VillagerEntityMCA villager, @Nullable String skin) {
        return skin != null && !skin.isEmpty() && SkinPicker.fits(fitted(villager), skin);
    }

    /** A fitted work skin for the villager's profession, or empty when no fitted skin exists. */
    public static Optional<String> pick(VillagerEntityMCA villager) {
        ClothingList list = ClothingList.getInstance();
        if (list == null || list.clothing == null) return Optional.empty();
        List<ClothingEntry> fitted = fitted(villager);
        Gender gender = villager.getGenetics().getGender();
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        String profession = professionId == null ? "minecraft:none" : professionId.toString();

        List<Clothing> own = new ArrayList<>();
        List<Clothing> civilian = new ArrayList<>();
        List<Clothing> any = new ArrayList<>();
        for (Clothing clothing : list.clothing.values()) {
            if (clothing == null || !SkinPicker.fits(fitted, clothing.getIdentifier())) continue;
            if (!SkinSelection.matchesGender(clothing, gender)) continue;
            any.add(clothing);
            if (sameProfession(clothing.profession, profession)) own.add(clothing);
            else if (civilian(clothing.profession)) civilian.add(clothing);
        }
        List<Clothing> pool = !own.isEmpty() ? own : !civilian.isEmpty() ? civilian : any;
        if (pool.isEmpty()) return Optional.empty();
        pool.sort(Comparator.comparing(Clothing::getIdentifier));
        Random random = new Random(villager.getUUID().getMostSignificantBits() ^ profession.hashCode());
        return Optional.of(pool.get(random.nextInt(pool.size())).getIdentifier());
    }

    /** Put a fitted work skin on a rig villager whose current skin does not fit its body. */
    public static void ensureFitted(VillagerEntityMCA villager) {
        if (villager == null || villager.isClothingLocked() || !applies(villager)) return;
        if (fits(villager, villager.getClothes())) return;
        pick(villager).ifPresent(villager::setClothes);
    }

    private static boolean sameProfession(@Nullable String authored, String profession) {
        if (authored == null || authored.isEmpty()) return false;
        return authored.equals(profession) || authored.equals(profession.replace(':', '.'));
    }

    private static boolean civilian(@Nullable String authored) {
        return authored == null || authored.isEmpty() || authored.equals("minecraft:none")
                || authored.equals("minecraft.none");
    }

    private static List<ClothingEntry> fitted(VillagerEntityMCA villager) {
        var life = TownsteadVillagers.get(villager).life();
        Heritage heritage = life.hasHeritage() ? life.heritage() : null;
        return BodyClothingResolver.fitted(rootId(villager), heritage);
    }

    @Nullable
    private static ResourceLocation rootId(VillagerEntityMCA villager) {
        return DataPackLang.parseId(TownsteadVillagers.get(villager).life().rootId());
    }
}
