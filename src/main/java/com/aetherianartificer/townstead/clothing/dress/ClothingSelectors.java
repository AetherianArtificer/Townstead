package com.aetherianartificer.townstead.clothing.dress;

import com.aetherianartificer.townstead.clothing.ClothingDefs;
import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicy;
import com.aetherianartificer.townstead.culture.CultureClothing;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Whether a policy selector admits a clothing entry that describes a stack, not a skin. */
public final class ClothingSelectors {

    private ClothingSelectors() {}

    public static boolean admits(WardrobePolicy.Selector selector, @Nullable ClothingEntry entry,
                                 CultureClothing culture, List<ClothingEntry> fitted) {
        if (selector == null) return false;
        if (selector.skin() != null) return false;
        if (selector.set() != null) return entry != null && ClothingDefs.members(selector.set()).contains(entry);
        if (selector.cultureSets()) {
            if (entry == null || culture == null) return false;
            for (ResourceLocation setId : culture.sets()) {
                if (ClothingDefs.members(setId).contains(entry)) return true;
            }
            return false;
        }
        if (selector.bodySets()) return entry != null && fitted != null && fitted.contains(entry);
        if (selector.query() == null || selector.query().isEmpty()) return true;
        return entry != null && selector.query().test(entry);
    }
}
