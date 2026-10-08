package com.aetherianartificer.townstead.client.expression;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.expression.ExpressionCue;
import com.aetherianartificer.townstead.expression.ExpressionCueS2CPayload;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small ephemeral client store: newest cue replaces the prior cue on the same entity. */
public final class ExpressionCueClientStore {
    private static final Map<Integer, Active> ACTIVE = new ConcurrentHashMap<>();

    private ExpressionCueClientStore() {}

    public static void accept(ExpressionCueS2CPayload payload) {
        long now = clientTicks();
        ExpressionCue.Kind kind = payload.kind() == ExpressionCue.Kind.ICON.ordinal()
                ? ExpressionCue.Kind.ICON : ExpressionCue.Kind.TEXT;
        int duration = Math.max(10, payload.durationTicks());
        int count = Math.max(1, Math.min(32, payload.iconCount()));
        int stagger = Math.max(0, Math.min(40, payload.iconStaggerTicks()));
        List<String> text = payload.textTranslations() == null || payload.textTranslations().isEmpty()
                ? List.of(payload.content()) : List.copyOf(payload.textTranslations());
        int textStagger = Math.max(0, Math.min(80, payload.textStaggerTicks()));
        long tail = kind == ExpressionCue.Kind.ICON
                ? (long) (count - 1) * stagger
                : (long) (text.size() - 1) * textStagger;
        long end = now + duration + tail;
        ACTIVE.put(payload.entityId(), new Active(payload.cueId(), kind, payload.content(), now, end,
                duration, payload.rise(), payload.drift(), payload.scale(), payload.color(), count,
                payload.iconSpread(), payload.iconVerticalSpread(), stagger, payload.iconScaleVariance(),
                text, payload.textSpread(), payload.textVerticalSpread(), textStagger,
                payload.textScaleVariance()));
        Townstead.LOGGER.info("[ExpressionCue] client accepted {} for entity {} ({} ticks)",
                payload.cueId(), payload.entityId(), payload.durationTicks());
    }

    public static Active get(int entityId) {
        Active active = ACTIVE.get(entityId);
        if (active != null && active.endTick() <= clientTicks()) {
            ACTIVE.remove(entityId, active);
            return null;
        }
        return active;
    }

    public static void tick() {
        long now = clientTicks();
        ACTIVE.entrySet().removeIf(entry -> entry.getValue().endTick() <= now);
    }

    public static void clear() { ACTIVE.clear(); }

    private static long clientTicks() {
        return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
    }

    public record Active(String cueId, ExpressionCue.Kind kind, String content, long startTick, long endTick,
                         int durationTicks, float rise, float drift, float scale, int color, int iconCount,
                         float iconSpread, float iconVerticalSpread, int iconStaggerTicks,
                         float iconScaleVariance, List<String> textTranslations, float textSpread,
                         float textVerticalSpread, int textStaggerTicks, float textScaleVariance) {
        public float progress(float partialTick) {
            return Math.max(0f, Math.min(1f, ageTicks(partialTick) / Math.max(1f, durationTicks)));
        }

        public float ageTicks(float partialTick) {
            // Subtract as longs first so an old world's game time cannot swallow fractional ticks.
            return (clientTicks() - startTick) + partialTick;
        }
    }
}
