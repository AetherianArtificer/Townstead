package com.aetherianartificer.townstead.mixin;

//? if neoforge {
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lifts the 2 MiB {@link net.minecraft.nbt.NbtAccounter} cap that MCA's
 * {@code GetVillageResponse} decode lambda inherits from
 * {@link ByteBufCodecs#COMPOUND_TAG}. Villages with many synthetic Townstead
 * buildings (docks, enclosures) carry full {@code blocks2} listings into the
 * server's village snapshot, which the client then has to decode in one go.
 * The vanilla {@link ByteBufCodecs#TRUSTED_COMPOUND_TAG} uses {@code Long.MAX_VALUE}
 * for the same shape of {@code CompoundTag} payload — server-to-client trusted
 * traffic, no different in semantics.
 *
 * <p>Targets the synthetic decoder lambda compiled from MCA's STREAM_CODEC
 * static initializer; the redirect is gated to that single GETSTATIC, so the
 * encoder lambda (which uses COMPOUND_TAG for write-only) is untouched.</p>
 *
 * <p>1.21.1-only: {@code TRUSTED_COMPOUND_TAG} does not exist in 1.20.1, and
 * MCA's 1.20.1 Forge build serves this payload through the legacy
 * {@code SimpleChannel} API rather than a StreamCodec, so the same bug does
 * not manifest there.</p>
 */
@Mixin(targets = "net.conczin.mca.network.s2c.GetVillageResponse", remap = false)
public class GetVillageResponseLargePacketMixin {
    @Redirect(
            method = "lambda$static$4",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/network/codec/ByteBufCodecs;COMPOUND_TAG:Lnet/minecraft/network/codec/StreamCodec;",
                    opcode = Opcodes.GETSTATIC
            ),
            require = 1,
            remap = false
    )
    private static StreamCodec<ByteBuf, CompoundTag> townstead$useTrustedCompoundTagCodec() {
        return ByteBufCodecs.TRUSTED_COMPOUND_TAG;
    }
}
//?} else if forge {
/*import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.conczin.mca.network.s2c.GetVillageResponse", remap = false)
public class GetVillageResponseLargePacketMixin {
    // No-op on 1.20.1: MCA 1.20.1 uses the legacy SimpleChannel API which
    // does not go through ByteBufCodecs.COMPOUND_TAG, so the 2 MiB NbtAccounter
    // ceiling does not gate this packet.
}
*///?}
