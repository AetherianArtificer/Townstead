package com.aetherianartificer.townstead.origin.ability;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → owning player: the gene ids of that player's toggle-mode abilities that
 * are currently ON. Transient (toggles reset on reload), so it carries only the live
 * on-set, rebuilt and resent on each flip. The controlling client needs this to
 * predict toggle-driven movement abilities locally; villagers are server-authoritative
 * and never receive it. Keyed by the player's network entity id; updates
 * {@code OriginClientStore}.
 */
//? if neoforge {
public record AbilityTogglesS2CPayload(int entityId, List<String> geneIds) implements CustomPacketPayload {
//?} else {
/*public record AbilityTogglesS2CPayload(int entityId, java.util.List<String> geneIds) {
*///?}

    //? if neoforge {
    public static final Type<AbilityTogglesS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "ability_toggles_s2c"));

    public static final StreamCodec<FriendlyByteBuf, AbilityTogglesS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AbilityTogglesS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "ability_toggles_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "ability_toggles_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeVarInt(geneIds.size());
        for (String geneId : geneIds) buf.writeUtf(geneId);
    }

    public static AbilityTogglesS2CPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int count = buf.readVarInt();
        List<String> geneIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) geneIds.add(buf.readUtf());
        return new AbilityTogglesS2CPayload(entityId, geneIds);
    }
}
