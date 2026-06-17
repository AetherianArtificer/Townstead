package com.aetherianartificer.townstead.dialogue;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.Origin;
import com.aetherianartificer.townstead.origin.OriginRegistry;
import com.aetherianartificer.townstead.origin.personality.PersonalityDef;
import com.aetherianartificer.townstead.origin.personality.PersonalityResolver;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Species-derived dialogue voice: a villager whose origin tree defines lines for a phrase speaks
 * those instead of MCA's generic pool, resolved entirely server-side from the data-pack lang sidecar
 * (so connecting clients need no resource pack, matching the rest of Townstead's data-driven content).
 *
 * <p>Lines live under {@code townstead_voice.<flattened-tree-id>.<phraseId>/<N>} (1-based, like MCA's
 * own {@code welcome/1} variants), where the tree id is an origin / lineage / ancestry / species id
 * with its {@code :} flattened to {@code .}. The chain is tried most-specific first, so a lineage or
 * (future) culture can override a single phrase while the species supplies the base voice; the first
 * level that has the phrase wins, and a phrase with no Townstead lines falls through to MCA.</p>
 */
public final class SpeciesVoice {

    private SpeciesVoice() {}

    private static final String PREFIX = "townstead_voice.";
    private static final int MAX_VARIANTS = 64;

    /** A species/origin-tree line for this phrase, or {@code null} to fall through to MCA's own. */
    public static MutableComponent line(VillagerEntityMCA villager, Player target, String phraseId, Object[] params) {
        if (phraseId == null || phraseId.isEmpty()) return null;
        for (String voice : voiceChain(villager)) {
            List<String> variants = variants(PREFIX + voice + "." + phraseId);
            if (!variants.isEmpty()) {
                return format(variants.get(villager.getRandom().nextInt(variants.size())), target, params);
            }
        }
        return null;
    }

    /**
     * Voice namespaces most specific first: the custom personality, then origin, lineage, ancestry,
     * species. A custom personality's own lines beat the species voice per phrase; a bare base-enum
     * personality contributes no tier (it falls straight through to the species voice).
     */
    private static List<String> voiceChain(VillagerEntityMCA villager) {
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        List<String> out = new ArrayList<>();
        PersonalityDef personality = PersonalityResolver.def(life.personalityId());
        if (personality != null) addFlat(out, personality.id());
        ResourceLocation oid = DataPackLang.parseId(life.originId());
        if (oid == null) return out;
        addFlat(out, oid);
        Origin origin = OriginRegistry.byId(oid);
        if (origin != null) {
            addFlat(out, origin.lineage());
            addFlat(out, origin.ancestry());
        }
        addFlat(out, OriginRegistry.effectiveSpecies(oid));
        return out;
    }

    private static void addFlat(List<String> out, ResourceLocation id) {
        if (id == null) return;
        String flat = id.getNamespace() + "." + id.getPath();
        if (!out.contains(flat)) out.add(flat);
    }

    /** Contiguous 1-based variants for a phrase key ({@code base/1}, {@code base/2}, ...). */
    private static List<String> variants(String base) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= MAX_VARIANTS; i++) {
            String v = DataPackLang.find(base + "/" + i, "en_us");
            if (v == null) break;
            out.add(v);
        }
        return out;
    }

    /** Substitute {@code %1$s} = player name (and any extra params) the way MCA's getTranslatable does. */
    private static MutableComponent format(String line, Player target, Object[] params) {
        if (line.indexOf('%') < 0) return Component.literal(line);
        Object[] args = new Object[(params == null ? 0 : params.length) + 1];
        args[0] = target == null ? "" : target.getName().getString();
        if (params != null) System.arraycopy(params, 0, args, 1, params.length);
        try {
            return Component.literal(String.format(line, args));
        } catch (Exception e) {
            return Component.literal(line);
        }
    }
}
