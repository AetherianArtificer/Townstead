package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;

/**
 * Permanent aliases for Career identifiers that shipped under names we no longer use.
 *
 * <p>Unlike the automatically inferred flat-skill aliases in {@link SkillDefs}, these aliases
 * describe intentional renames. Keeping them in one place lets saves, commands, and datapacks
 * cross the rename without preserving the old vocabulary in the canonical data layout.</p>
 */
public final class CareerIdAliases {
    private static final ResourceLocation BAKER = id("townstead:baker");

    private CareerIdAliases() {}

    /** The canonical author id for a path within a profession. */
    public static String canonicalPath(ResourceLocation professionId, String pathId) {
        if (!BAKER.equals(professionId) || pathId == null) return pathId;
        return switch (pathId) {
            case "confiturier" -> "jam_maker";
            case "patissier" -> "pastry_chef";
            default -> pathId;
        };
    }

    /** The canonical id for a path-scoped skill, including skills added by other datapacks. */
    public static ResourceLocation canonicalSkill(ResourceLocation skillId) {
        if (skillId == null || !"townstead".equals(skillId.getNamespace())) return skillId;
        String path = skillId.getPath();
        String canonical = replacePrefix(path, "baker/confiturier/", "baker/jam_maker/");
        canonical = replacePrefix(canonical, "baker/patissier/", "baker/pastry_chef/");
        return canonical.equals(path) ? skillId
                : ResourceLocation.tryParse(skillId.getNamespace() + ":" + canonical);
    }

    private static String replacePrefix(String value, String legacy, String canonical) {
        return value.startsWith(legacy) ? canonical + value.substring(legacy.length()) : value;
    }

    private static ResourceLocation id(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) throw new IllegalArgumentException("Invalid built-in Career id " + value);
        return parsed;
    }
}
