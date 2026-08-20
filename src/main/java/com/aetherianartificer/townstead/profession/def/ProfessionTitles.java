package com.aetherianartificer.townstead.profession.def;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Build titles: the Community Spirit pattern applied to people. A profession def's
 * {@code titles} name completed skill builds — spec into the wood-fire branch of Cook and the
 * record reads "Rotisseur (Cook)"; a Guard with the command build reads "Captain (Guard)".
 * Pure flavour over a stable base profession, resolved from the learned skill set: a title
 * applies when ALL its skills are learned; the title with the most skills wins, ties by id.
 * Replaced wholesale each datapack reload alongside {@link ProfessionDefs}.
 */
public final class ProfessionTitles {

    /** One named build within a profession. */
    public record Title(ResourceLocation professionId, String id, Component name,
                        List<ResourceLocation> skills) {
        public Title {
            skills = List.copyOf(skills);
        }
    }

    private static volatile Map<ResourceLocation, List<Title>> BY_PROFESSION = Map.of();

    private ProfessionTitles() {}

    public static void replaceAll(Map<ResourceLocation, List<Title>> next) {
        BY_PROFESSION = Map.copyOf(next);
    }

    public static List<Title> titlesFor(ResourceLocation professionId) {
        return BY_PROFESSION.getOrDefault(professionId, List.of());
    }

    /** The best-earned title for a learned-skill set, or null when no build is complete. */
    @Nullable
    public static Title resolve(ResourceLocation professionId, Predicate<ResourceLocation> learned) {
        Title best = null;
        for (Title title : titlesFor(professionId)) {
            if (title.skills().isEmpty()) continue;
            boolean complete = true;
            for (ResourceLocation skill : title.skills()) {
                if (!learned.test(skill)) {
                    complete = false;
                    break;
                }
            }
            if (!complete) continue;
            if (best == null
                    || title.skills().size() > best.skills().size()
                    || (title.skills().size() == best.skills().size()
                        && title.id().compareTo(best.id()) < 0)) {
                best = title;
            }
        }
        return best;
    }
}
