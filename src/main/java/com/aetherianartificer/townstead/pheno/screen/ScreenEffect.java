package com.aetherianartificer.townstead.pheno.screen;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Screen cues sent by {@code pheno:screen_effect}. The server only names the cue; the receiving
 * client draws it ({@link Client}) and scales it by the player's own screen-effect and
 * reduced-motion settings, so a cue can never be stronger than the player allows.
 */
public final class ScreenEffect {

    public enum Kind {
        /** A full-screen flash that fades out. */
        FLASH,
        /** Darkened, tinted edges that swell and ebb. */
        VIGNETTE,
        /** A full-screen colour wash that swells and ebbs. */
        TINT,
        /** The camera trembles, settling over the duration. */
        SHAKE,
        /** The view widens (positive) or narrows (negative) and returns. */
        FOV;

        public static Kind byKey(String raw) {
            try {
                return raw == null ? null : valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private ScreenEffect() {}

    public static void send(ServerPlayer player, Kind kind, int color, float intensity, int duration) {
        ScreenEffectS2CPayload payload = new ScreenEffectS2CPayload(kind.ordinal(), color, intensity, duration);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    /**
     * Client state: the cues currently playing. Plain data and arithmetic only; the drawing and
     * camera hooks live in {@code client.screen.ScreenEffectRender}.
     */
    public static final class Client {
        public record Active(Kind kind, int color, float intensity, int duration, long startedAt) {
            /** 0 to 1 over the cue's life. */
            public float progress(long now, float partial) {
                return Math.min(1f, Math.max(0f, (now - startedAt + partial) / Math.max(1, duration)));
            }

            /** How strong the cue is right now: flashes and shakes fade, the rest swell and ebb. */
            public float envelope(long now, float partial) {
                float t = progress(now, partial);
                return switch (kind) {
                    case FLASH, SHAKE -> 1f - t;
                    default -> (float) Math.sin(Math.PI * t);
                };
            }
        }

        private static final List<Active> ACTIVE = new ArrayList<>();
        private static long clock;

        private Client() {}

        public static synchronized void start(Kind kind, int color, float intensity, int duration) {
            ACTIVE.add(new Active(kind, color, intensity, Math.max(1, duration), clock));
        }

        /** Advance one client tick and drop finished cues. */
        public static synchronized void tick() {
            clock++;
            for (Iterator<Active> it = ACTIVE.iterator(); it.hasNext(); ) {
                Active a = it.next();
                if (clock - a.startedAt() > a.duration()) it.remove();
            }
        }

        public static synchronized List<Active> active() {
            return List.copyOf(ACTIVE);
        }

        public static long clock() {
            return clock;
        }

        public static synchronized void clear() {
            ACTIVE.clear();
        }
    }
}
