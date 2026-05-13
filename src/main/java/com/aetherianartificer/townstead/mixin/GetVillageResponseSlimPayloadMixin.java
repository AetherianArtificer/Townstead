package com.aetherianartificer.townstead.mixin;

//? if neoforge {
import com.aetherianartificer.townstead.compat.mca.VillageSnapshotSlimmer;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Strips per-position {@code x/y/z} ints from every building's {@code blocks2}
 * entry in MCA's {@code GetVillageResponse} wire payload, before the
 * encoder writes the village {@link CompoundTag}. The encoder lambda's first
 * {@code StreamCodec.encode(buf, value)} call is the village snapshot, which
 * is the one that bloats; subsequent encode calls handle Rank, ids, tasks, and
 * building types and are left untouched via {@code ordinal = 0}.
 *
 * <p>The client tooltip in {@code BlueprintScreen} only reads
 * {@code List<BlockPos>.size()} per block-type key, so substituting empty
 * {@link CompoundTag}s for real position records preserves the displayed
 * count while collapsing each position's wire cost from ~30 bytes to 1.</p>
 *
 * <p>Companion to {@code GetVillageResponseLargePacketMixin}, which raises the
 * client decode cap as a belt-and-suspenders measure in case the slim payload
 * still grows large (many residents, many tasks, many building types).</p>
 *
 * <p>1.21.1-only: 1.20.1 Forge MCA encodes this payload through the legacy
 * {@code SimpleChannel}/{@code NbtDataMessage} path, which uses different
 * machinery; if the same bloat surfaces there, it needs a separate fix.</p>
 */
@Mixin(targets = "net.conczin.mca.network.s2c.GetVillageResponse", remap = false)
public class GetVillageResponseSlimPayloadMixin {
    @ModifyArg(
            method = "lambda$static$2",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V",
                    ordinal = 0
            ),
            index = 1,
            require = 1,
            remap = false
    )
    private static Object townstead$slimVillageSnapshot(Object value) {
        if (value instanceof CompoundTag tag) {
            return VillageSnapshotSlimmer.slim(tag);
        }
        return value;
    }
}
//?} else if forge {
/*import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.conczin.mca.network.s2c.GetVillageResponse", remap = false)
public class GetVillageResponseSlimPayloadMixin {
    // No-op on 1.20.1: MCA Forge 1.20.1 serializes GetVillageResponse via
    // SimpleChannel / NbtDataMessage, which does not go through the
    // ByteBufCodecs.COMPOUND_TAG encode call this mixin targets.
}
*///?}
