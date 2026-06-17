package com.aetherianartificer.townstead.origin.attachment;

import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

/**
 * Server side of attachment sync: pushes the manifest to a player (on join / data
 * reload) and answers blob requests by chunking the cached bytes.
 */
public final class AttachmentSync {

    private AttachmentSync() {}

    public static void sendManifest(ServerPlayer player) {
        send(player, new AttachmentManifestS2CPayload(
                AttachmentServerData.definitions(), AttachmentServerData.slots(),
                AttachmentServerData.namedTextures()));
    }

    public static void handleRequest(ServerPlayer player, List<String> hashes) {
        int chunkSize = AttachmentChunkS2CPayload.CHUNK_SIZE;
        for (String sha1 : hashes) {
            AttachmentServerData.Blob blob = AttachmentServerData.blob(sha1);
            if (blob == null) continue;
            byte[] bytes = blob.bytes();
            int total = Math.max(1, (bytes.length + chunkSize - 1) / chunkSize);
            for (int i = 0; i < total; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, bytes.length);
                send(player, new AttachmentChunkS2CPayload(sha1, i, total, blob.kind(),
                        Arrays.copyOfRange(bytes, start, end)));
            }
        }
    }

    private static void send(ServerPlayer player, Object payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                (net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
