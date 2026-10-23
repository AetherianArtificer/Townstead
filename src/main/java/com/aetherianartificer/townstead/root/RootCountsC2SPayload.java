package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Client to server, when the root browser opens: how many of each root live in this world? The
 * server counts the players and villagers it has loaded and answers with a
 * {@link RootCountsS2CPayload}, which fills the browser's "In this world" shortcut.
 */
//? if neoforge {
public record RootCountsC2SPayload(int unused) implements CustomPacketPayload {
//?} else {
/*public record RootCountsC2SPayload(int unused) {
*///?}

    //? if neoforge {
    public static final Type<RootCountsC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "root_counts_c2s"));
    public static final StreamCodec<FriendlyByteBuf, RootCountsC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RootCountsC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public RootCountsC2SPayload() {
        this(0);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(unused);
    }

    public static RootCountsC2SPayload read(FriendlyByteBuf buf) {
        return new RootCountsC2SPayload(buf.readVarInt());
    }

    /** Counts on the server thread and answers. */
    public static void handle(ServerPlayer sp, RootCountsC2SPayload payload) {
        Map<String, Integer> counts = new HashMap<>();
        for (ServerLevel level : sp.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                String id = entity instanceof Player player ? PlayerRoot.getRootId(player)
                        : entity instanceof VillagerEntityMCA villager
                                ? com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).life().rootId()
                                : null;
                if (id != null && !id.isEmpty()) counts.merge(id, 1, Integer::sum);
            }
        }
        RootCountsS2CPayload reply = new RootCountsS2CPayload(counts);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, reply);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(sp, reply);
        *///?}
    }
}
