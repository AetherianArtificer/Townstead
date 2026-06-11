package com.aetherianartificer.townstead.profession.def;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A data-driven skill within a profession: the tier that gates it, the prerequisite skills that
 * must be learned first ({@code requires}, AND), the skills it is mutually exclusive with
 * ({@code exclusiveWith}, branching specializations), its point cost, the capabilities it grants
 * once learned, and the semantic animation intent it expresses (never a model transform).
 */
public record SkillDef(
        ResourceLocation id,
        Component displayName,
        @Nullable Component description,
        ResourceLocation profession,
        int tier,
        List<ResourceLocation> requires,
        List<ResourceLocation> exclusiveWith,
        int cost,
        List<SkillGrant> grants,
        @Nullable ResourceLocation animation) {
}
