package com.aetherianartificer.townstead.client.animation.emote.loader;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.EmoteRegistry;
import com.aetherianartificer.townstead.client.animation.emote.ParsedEmote;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Reflective view onto Emotecraft's {@code ClientEmoteAPI.clientEmoteList()} —
 * the set of emotes the local player currently has loaded (built-ins plus their
 * personal {@code <gamedir>/emotes/} folder). Used by the Pose picker so the
 * player can hand any of their installed Emotecraft animations to a villager
 * without first dropping it into a Townstead resource pack.
 *
 * <p>Each returned {@code KeyframeAnimation} is parsed into a {@link
 * ParsedEmote} and added to the {@link EmoteRegistry} as a transient entry, so
 * the existing trigger pipeline can find it by id.</p>
 */
public final class EmotecraftEmoteList {
    private EmotecraftEmoteList() {}

    public record Entry(ResourceLocation id, UUID uuid, String displayName, byte[] iconBytes) {}

    /**
     * The player's configured Emotecraft fast-menu slot assignments: a
     * {@code UUID[pages][8]} where each entry is the UUID of the emote bound
     * to that page/slot, or {@code null} if the slot is empty. Returns
     * {@code null} if Emotecraft isn't installed or its config isn't
     * reachable — callers should fall back to a simple alphabetical layout.
     */
    public static UUID[][] fastMenuSlots() {
        if (!EmoteReflection.isAvailable()
                || EmoteReflection.emoteInstanceConfig == null
                || EmoteReflection.clientConfigFastMenuEmotes == null) {
            return null;
        }
        try {
            Object config = EmoteReflection.emoteInstanceConfig.get(null);
            if (config == null) return null;
            Object slots = EmoteReflection.clientConfigFastMenuEmotes.get(config);
            return slots instanceof UUID[][] grid ? grid : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<Entry> snapshotAndRegister() {
        if (!EmoteReflection.isAvailable()) return Collections.emptyList();

        try {
            Object raw = EmoteReflection.clientEmoteApiList.invoke(null);
            if (!(raw instanceof Collection<?> animations)) return Collections.emptyList();

            List<Entry> out = new ArrayList<>(animations.size());
            for (Object animation : animations) {
                if (animation == null) continue;
                Entry entry = registerOne(animation);
                if (entry != null) out.add(entry);
            }
            out.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
            return out;
        } catch (Throwable t) {
            Townstead.LOGGER.debug("[AnimationBridge] failed to snapshot Emotecraft client list ({})",
                    t.getMessage());
            return Collections.emptyList();
        }
    }

    private static Entry registerOne(Object keyframeAnimation) {
        try {
            UUID animUuid = null;
            if (EmoteReflection.animGetUuid != null) {
                Object u = EmoteReflection.animGetUuid.invoke(keyframeAnimation);
                if (u instanceof UUID uu) animUuid = uu;
            }
            String suffix = animUuid != null
                    ? animUuid.toString()
                    : Long.toHexString(System.identityHashCode(keyframeAnimation) & 0xFFFFFFFFL);
            ResourceLocation id = synthId(suffix);

            ParsedEmote existing = EmoteRegistry.get(id).orElse(null);
            ParsedEmote parsed = existing != null
                    ? existing
                    : EmotecraftEmoteLoader.parseAnimation(id, keyframeAnimation);
            if (parsed == null) return null;
            if (existing == null) EmoteRegistry.putTransient(parsed);
            return new Entry(id, animUuid, parsed.displayName(), extractIconBytes(keyframeAnimation));
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] extractIconBytes(Object keyframeAnimation) {
        if (EmoteReflection.animExtraData == null) return null;
        try {
            Object raw = EmoteReflection.animExtraData.get(keyframeAnimation);
            if (!(raw instanceof HashMap<?, ?> map)) return null;
            Object iconData = map.get("iconData");
            if (iconData instanceof ByteBuffer buffer) {
                ByteBuffer dup = buffer.duplicate();
                dup.rewind();
                int remaining = dup.remaining();
                if (remaining <= 0 || remaining > 1024 * 1024) return null;
                byte[] bytes = new byte[remaining];
                dup.get(bytes);
                return bytes;
            }
            if (iconData instanceof byte[] direct) {
                return direct.length > 0 && direct.length <= 1024 * 1024 ? direct : null;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static ResourceLocation synthId(String suffix) {
        String safe = suffix.replaceAll("[^a-z0-9_.-]", "_").toLowerCase(Locale.ROOT);
        String path = "emotecraft_runtime/" + safe;
        //? if neoforge {
        return ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, path);
        //?} else {
        /*return new ResourceLocation(Townstead.MOD_ID, path);
        *///?}
    }
}
