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
 * <p><b>Legacy MCA only</b> ({@code TownsteadMixinPlugin} gates it off the
 * floor-system build). There the real payload bloat is fixed at the source (no
 * building type lists generic structural material, so ordinary houses do not record
 * their walls) and {@code GetVillageResponseLargePacketMixin} raises the decode cap for
 * any legacy save data, so the slimmer is unnecessary — and keeping this wire
 * rewrite off that version avoids disturbing the block geometry the new map
 * renderer reads. {@link VillageSnapshotSlimmer} still truncates to real,
 * decodable positions (not empty tags) should it ever run on a new build.</p>
 *
 * <p>On 1.20.1 Forge, MCA encodes this payload through the legacy
 * {@code SimpleChannel}/{@code NbtDataMessage} path, so the Forge branch
 * slims the {@code CompoundTag} handed to {@code NbtDataMessage}'s
 * constructor instead.</p>
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
/*import com.aetherianartificer.townstead.compat.mca.VillageSnapshotSlimmer;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.conczin.mca.network.s2c.GetVillageResponse", remap = false)
public class GetVillageResponseSlimPayloadMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/conczin/mca/network/NbtDataMessage;<init>(Lnet/minecraft/nbt/CompoundTag;)V"
            ),
            index = 0,
            require = 1,
            remap = false
    )
    private static CompoundTag townstead$slimVillageSnapshot(CompoundTag value) {
        return VillageSnapshotSlimmer.slim(value);
    }
}
*///?}
