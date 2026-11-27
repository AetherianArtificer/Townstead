package com.aetherianartificer.townstead.root.modifier;

import com.aetherianartificer.townstead.root.gene.types.ModifierGeneType;
import com.aetherianartificer.townstead.pheno.capability.CapabilityKey;
import com.aetherianartificer.townstead.pheno.capability.Op;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One place that maps a {@link ModifierGeneType.Modifier} (plus optional discriminator) to its
 * {@link CapabilityKey}, so the emit side ({@code GeneCapabilitySource}) and the read side
 * ({@code PhenoHooks}) can never drift. Keys are always scalar; the identity is unused under
 * apply-to-base resolution (the live base seeds the fold).
 */
public final class ModifierCapability {

    private ModifierCapability() {}

    public static CapabilityKey key(ModifierGeneType.Modifier kind) {
        return key(kind, null);
    }

    public static CapabilityKey key(ModifierGeneType.Modifier kind, @Nullable ResourceLocation discriminator) {
        if (kind == ModifierGeneType.Modifier.CUSTOM && discriminator != null) {
            return CapabilityKey.scalar(ResourceLocation.tryParse("townstead_roots:"
                    + "modifier/" + discriminator.getNamespace() + "/" + discriminator.getPath()));
        }
        String path = "modifier/" + kind.key() + (discriminator != null ? "/" + discriminator : "");
        return CapabilityKey.scalar(ResourceLocation.tryParse("townstead_roots:" + path));
    }

    /** Maps a parsed modifier op onto the capability fold op. */
    public static Op op(ModifierGeneType.Op op) {
        return switch (op) {
            case ADD -> Op.ADD;
            case SET -> Op.REPLACE;
            case MIN -> Op.MIN;
            case MAX -> Op.MAX;
            case MULTIPLY -> Op.MULTIPLY;
        };
    }
}
