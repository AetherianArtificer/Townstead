package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.power.PowerComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A data-driven skill within a profession: the tier that gates it, the prerequisite skills that
 * must be learned first ({@code requires}, AND), the skills it is mutually exclusive with
 * ({@code exclusiveWith}, branching specializations), its point cost, the capabilities it grants
 * once learned, the pheno power it expresses while active, and the semantic animation intent it
 * expresses (never a model transform).
 *
 * <p>{@code grants} feed the passive capability resolver; {@code power} is a full pheno
 * component (any registered gene/power type) fed through {@code SkillPowerSource}, so a skill
 * can carry abilities, modifiers, immunities, triggers, and every other behavior the power
 * layer supports.</p>
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
        @Nullable ResourceLocation animation,
        @Nullable ResourceLocation skillGroup,
        @Nullable PowerComponent power,
        @Nullable ResourceLocation icon) {

    /** Compatibility constructor for v1 definitions and integrations. */
    public SkillDef(ResourceLocation id, Component displayName, @Nullable Component description,
                    ResourceLocation profession, int tier, List<ResourceLocation> requires,
                    List<ResourceLocation> exclusiveWith, int cost, List<SkillGrant> grants,
                    @Nullable ResourceLocation animation) {
        this(id, displayName, description, profession, tier, requires, exclusiveWith,
                cost, grants, animation, null, null, null);
    }
}
