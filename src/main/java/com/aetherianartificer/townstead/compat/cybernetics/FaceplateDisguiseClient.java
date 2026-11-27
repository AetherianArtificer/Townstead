package com.aetherianartificer.townstead.compat.cybernetics;

import com.aetherianartificer.townstead.client.root.RootClientStore;
import net.conczin.mca.MCAClient;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's side of {@link FaceplateDisguise}. While a disguised player renders, MCA's player
 * data for them is the named player's, and Townstead's Root lookups for them read the named
 * player's Root. Ticks, hitboxes and abilities keep the wearer's own.
 */
public final class FaceplateDisguiseClient {

    /** {@code shown} null: an alias that names no one, so the plain Cybernetics disguise. */
    private record Disguise(UUID shown) {}

    private record Frame(int entityId, @Nullable Disguise disguise, UUID owner, boolean had,
                         @Nullable VillagerLike<?> own) {}

    private static final Map<Integer, Disguise> BY_ENTITY = new ConcurrentHashMap<>();
    private static final Deque<Frame> RENDERING = new ArrayDeque<>();

    private FaceplateDisguiseClient() {}

    public static void set(int entityId, boolean worn, @Nullable UUID shown) {
        if (worn) {
            BY_ENTITY.put(entityId, new Disguise(shown));
            if (shown == null) RootClientStore.remove(FaceplateDisguise.dataId(entityId));
        } else {
            BY_ENTITY.remove(entityId);
            RootClientStore.remove(FaceplateDisguise.dataId(entityId));
        }
    }

    public static void clear() {
        BY_ENTITY.clear();
        RENDERING.clear();
    }

    /** Whether this player's alias names a player here, so Cybernetics' own disguise skin stays off. */
    public static boolean showsPlayer(Entity entity) {
        Disguise disguise = BY_ENTITY.get(entity.getId());
        return disguise != null && disguise.shown() != null;
    }

    /** The named player's UUID while {@code entity} renders disguised as them, else null. */
    @Nullable
    public static UUID shownWhileRendering(Entity entity) {
        Frame frame = RENDERING.peek();
        if (frame == null || frame.entityId() != entity.getId() || frame.disguise() == null) return null;
        return frame.disguise().shown();
    }

    /** The id Root lookups for {@code entityId} read: the shown player's while it renders disguised. */
    public static int dataId(int entityId) {
        Frame frame = RENDERING.peek();
        if (frame == null || frame.entityId() != entityId || frame.disguise() == null
                || frame.disguise().shown() == null) return entityId;
        return FaceplateDisguise.dataId(entityId);
    }

    public static void beginRender(Entity entity) {
        Disguise disguise = entity instanceof Player ? BY_ENTITY.get(entity.getId()) : null;
        if (disguise == null) {
            RENDERING.push(new Frame(entity.getId(), null, entity.getUUID(), false, null));
            return;
        }
        UUID owner = entity.getUUID();
        boolean had = MCAClient.playerData.containsKey(owner);
        VillagerLike<?> own = MCAClient.playerData.get(owner);
        VillagerLike<?> shown = disguise.shown() == null ? null : MCAClient.playerData.get(disguise.shown());
        if (shown != null) MCAClient.playerData.put(owner, shown);
        else MCAClient.playerData.remove(owner);
        RENDERING.push(new Frame(entity.getId(), disguise, owner, had, own));
    }

    public static void endRender() {
        Frame frame = RENDERING.poll();
        if (frame == null || frame.disguise() == null) return;
        if (frame.had()) MCAClient.playerData.put(frame.owner(), frame.own());
        else MCAClient.playerData.remove(frame.owner());
    }
}
