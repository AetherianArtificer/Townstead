package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * What to call a slotted ability, and what to draw for it.
 *
 * <p>One id space, two registries behind it. A career ability's power is keyed by its SKILL id and a
 * Root ability's by its GENE id (see {@code SkillPowerSource} and {@code GenePowerSource}), so a
 * slot holds an id that could belong to either. Everything that has to name a slot needs this same
 * two-step, which is why it is here rather than inlined at each call site.</p>
 */
public final class AbilityNames {

    private AbilityNames() {}

    /**
     * The authored name, from whichever registry owns the id.
     *
     * <p>The gene lookup was missing, so every Root ability fell through to {@code prettify} and
     * arrived as its own id with the underscores taken out &mdash; "Deepwood gnome vanish unveil".
     * Genes carry a {@code display_name} like everything else; nothing had ever asked for it.</p>
     */
    public static String display(ResourceLocation id) {
        if (id == null) return "";
        SkillDef skill = SkillDefs.byId(id);
        if (skill != null && skill.displayName() != null) return skill.displayName().getString();
        com.aetherianartificer.townstead.root.gene.Gene gene =
                com.aetherianartificer.townstead.root.gene.GeneRegistry.byId(id);
        if (gene != null && gene.displayName() != null) return gene.displayName().getString();
        String key = "townstead.gene." + id.getPath();
        if (Language.getInstance().has(key)) return Component.translatable(key).getString();
        return prettify(id.getPath());
    }

    /** The item a slot draws, or empty when nothing authored one. */
    public static String icon(ResourceLocation id) {
        if (id == null) return "";
        SkillDef skill = SkillDefs.byId(id);
        if (skill != null && skill.icon() != null) return skill.icon().toString();
        com.aetherianartificer.townstead.root.gene.Gene gene =
                com.aetherianartificer.townstead.root.gene.GeneRegistry.byId(id);
        return gene != null && gene.icon() != null ? gene.icon().toString() : "";
    }

    /**
     * Two letters to draw when nothing authored an icon.
     *
     * <p>NOT a shared fallback sprite. One symbol for every Root ability would make a grid of sixty
     * identical cells, which is exactly the failure the grid was meant to fix &mdash; it would stop
     * looking broken without becoming any more scannable. Initials are derived from the name, so
     * every cell differs, and they degrade to something readable rather than to a shrug.</p>
     *
     * <p>Takes the first letter of each of the last two words, so "Deepwood gnome vanish" reads GV
     * rather than DG: the distinguishing part of these names is almost always at the end.</p>
     */
    public static String initials(ResourceLocation id) {
        return initialsOf(display(id));
    }

    /**
     * The same, from a name the caller already has.
     *
     * <p>A pure string function so the CLIENT can call it. Datapack registries are a server-side
     * thing, and the wheel already receives every name it draws.</p>
     */
    public static String initialsOf(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] words = name.trim().split("\\s+");
        // A trailing NUMBER is the distinguishing part, so it wins over initials. "Quick Cast Slot
        // 01" through "... 15" all reduce to "S0" otherwise, which is fifteen identical cells on a
        // surface whose whole job is telling them apart.
        String last = words[words.length - 1];
        if (words.length > 1 && last.chars().allMatch(Character::isDigit)) {
            return last.length() <= 2 ? last : last.substring(last.length() - 2);
        }
        if (words.length == 1) {
            return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase(
                    java.util.Locale.ROOT);
        }
        String first = words[words.length - 2].substring(0, 1);
        String second = words[words.length - 1].substring(0, 1);
        return (first + second).toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Where an ability came from, as a heading the picker can group under.
     *
     * <p>A career ability answers with its profession; anything else came from the player's Root.
     * That is the only split a person browsing hundreds of abilities actually thinks in.</p>
     */
    public static String source(ResourceLocation id) {
        SkillDef skill = id == null ? null : SkillDefs.byId(id);
        if (skill != null && skill.profession() != null) {
            com.aetherianartificer.townstead.profession.def.ProfessionDef def =
                    com.aetherianartificer.townstead.profession.def.ProfessionDefs.byId(
                            skill.profession());
            if (def != null && def.displayName() != null) return def.displayName().getString();
            return prettify(skill.profession().getPath());
        }
        return Component.translatable("townstead.ability.source.root").getString();
    }

    /**
     * A cost resource's own short name: "Veil", not "Deepwood gnome vanish veil".
     *
     * <p>A resource is a COMPANION gene scoped under the ability that owns it, so its id repeats
     * that ability's whole name before saying anything new. Prettifying the full path therefore
     * produced a cost line that restated the thing you were already looking at. The companion is
     * registered with its final segment as a display name, so asking the gene registry gives the one
     * word that matters; the path fallback trims to that segment for the same reason.</p>
     */
    public static String resource(ResourceLocation id) {
        if (id == null) return "";
        String key = "townstead.resource." + id.getPath();
        if (Language.getInstance().has(key)) return Component.translatable(key).getString();
        com.aetherianartificer.townstead.root.gene.Gene gene =
                com.aetherianartificer.townstead.root.gene.GeneRegistry.byId(id);
        if (gene != null && gene.displayName() != null) {
            return prettify(gene.displayName().getString());
        }
        String path = id.getPath();
        int cut = path.lastIndexOf('/');
        return prettify(cut >= 0 && cut < path.length() - 1 ? path.substring(cut + 1) : path);
    }

    private static String prettify(String path) {
        String spaced = path.replace('_', ' ').replace('/', ' ');
        return spaced.isEmpty() ? spaced
                : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
