package com.aetherianartificer.townstead.dialogue;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import com.aetherianartificer.townstead.root.Root;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.personality.PersonalityDef;
import com.aetherianartificer.townstead.root.personality.PersonalityResolver;
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
            String base = PREFIX + voice + "." + phraseId;
            String exact = DataPackLang.find(base, locale(target));
            if (exact != null) return format(exact, target, params);
            List<String> variants = variants(base, locale(target));
            if (!variants.isEmpty()) {
                return format(variants.get(villager.getRandom().nextInt(variants.size())), target, params);
            }
        }
        // A data-only profession pack may define the selected phrase directly. This is checked
        // after every voice tier so a personality or Root can still replace it.
        String direct = DataPackLang.find(phraseId, locale(target));
        if (direct != null) return format(direct, target, params);
        List<String> directVariants = variants(phraseId, locale(target));
        if (!directVariants.isEmpty()) {
            return format(directVariants.get(villager.getRandom().nextInt(directVariants.size())), target, params);
        }
        return null;
    }

    /**
     * Voice namespaces most specific first: the custom personality, its MCA base personality,
     * then origin, lineage, ancestry, and species. Including the base personality makes built-in
     * and addon-registered personalities extensible from data-pack language sidecars too; when no
     * sidecar line exists, MCA's resource-pack personality resolver remains the final fallback.
     */
    private static List<String> voiceChain(VillagerEntityMCA villager) {
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        List<String> out = new ArrayList<>();
        PersonalityDef personality = PersonalityResolver.def(life.personalityId());
        if (personality != null) addFlat(out, personality.id());
        addFlat(out, DataPackLang.parseId(McaPersonalityCompat.id(
                villager.getVillagerBrain().getPersonality())));
        ResourceLocation oid = DataPackLang.parseId(life.rootId());
        if (oid == null) return out;
        addFlat(out, oid);
        Root origin = RootRegistry.byId(oid);
        if (origin != null) {
            addFlat(out, origin.lineage());
            addFlat(out, origin.ancestry());
        }
        addFlat(out, RootRegistry.effectiveSpecies(oid));
        return out;
    }

    private static void addFlat(List<String> out, ResourceLocation id) {
        if (id == null) return;
        String flat = id.getNamespace() + "." + id.getPath();
        if (!out.contains(flat)) out.add(flat);
    }

    /** Contiguous 1-based variants for a phrase key ({@code base/1}, {@code base/2}, ...). */
    private static List<String> variants(String base, String locale) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= MAX_VARIANTS; i++) {
            String v = DataPackLang.find(base + "/" + i, locale);
            if (v == null) break;
            out.add(v);
        }
        return out;
    }

    private static String locale(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            //? if >=1.21 {
            return serverPlayer.clientInformation().language();
            //?} else {
            /*return serverPlayer.getLanguage();
            *///?}
        }
        return "en_us";
    }

    /** Substitute {@code %1$s} = player name (and any extra params) the way MCA's getTranslatable does. */
    private static MutableComponent format(String line, Player target, Object[] params) {
        if (line.indexOf('%') < 0) return Component.literal(line);
        Object[] args = new Object[(params == null ? 0 : params.length) + 1];
        args[0] = McaPlayerName.of(target);
        if (params != null) System.arraycopy(params, 0, args, 1, params.length);
        try {
            return Component.literal(String.format(line, args));
        } catch (Exception e) {
            return Component.literal(line);
        }
    }
}
