package com.aetherianartificer.townstead.root;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Everything visible about a founding, so the editor can show exactly the individual Apply will make:
 * MCA's body floats (in {@link RootGenes} order), the hair dye, the starting eye colours when the
 * people have an editable iris, an outfit when the body needs one (empty keeps the current one), and
 * the expressed genes and hair policy in the form the world renderer already reads.
 */
public record FoundingLook(float[] floats, int hairDye, boolean iris, int irisTint, int irisRight,
                           String clothes, ExpressedGenesS2CPayload genes) {

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(floats.length);
        for (float f : floats) buf.writeFloat(f);
        buf.writeInt(hairDye);
        buf.writeBoolean(iris);
        buf.writeInt(irisTint);
        buf.writeInt(irisRight);
        buf.writeUtf(clothes);
        genes.write(buf);
    }

    public static FoundingLook read(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 64);
        float[] floats = new float[n];
        for (int i = 0; i < n; i++) floats[i] = buf.readFloat();
        return new FoundingLook(floats, buf.readInt(), buf.readBoolean(), buf.readInt(), buf.readInt(),
                buf.readUtf(), ExpressedGenesS2CPayload.read(buf));
    }
}
