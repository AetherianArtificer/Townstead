package com.aetherianartificer.townstead.client.species;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;

/**
 * Which face frame a bearer wears this render, shared by both face paths (a rig's overlay quads in
 * {@link SpeciesFace} and a humanoid body's eye swap in {@link HumanoidEyes}) so a skeletownie and a
 * stalker blink and react the same way.
 *
 * <p>Frames run in a fixed order — eyes {@code [open, blink, happy, unhappy]}, mouth
 * {@code [neutral, happy, unhappy]}. Blinking is UUID-phased so a crowd doesn't blink in unison, and
 * the mood expression reacts to a CHANGE in MCA's mood value rather than its standing level: a
 * smile or frown flashes for a moment when something moves the villager, then relaxes to neutral —
 * no permanent grin.</p>
 */
public final class FaceExpression {

    private FaceExpression() {}

    public static final int EYES_OPEN = 0;
    public static final int EYES_BLINK = 1;
    public static final int EYES_HAPPY = 2;
    public static final int EYES_UNHAPPY = 3;

    public static final int MOUTH_NEUTRAL = 0;
    public static final int MOUTH_HAPPY = 1;
    public static final int MOUTH_UNHAPPY = 2;

    private record Reaction(int lastMood, long untilTick, int sign) {}
    private static final java.util.Map<Integer, Reaction> REACTIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int REACTION_TICKS = 50;   // ~2.5s of expression after a mood change

    /** The eye frame index: closed while asleep or mid-blink, else the mood reaction, else open. */
    public static int eyeFrame(LivingEntity entity) {
        if (closed(entity)) return EYES_BLINK;
        int reaction = reactionSign(entity);
        return reaction > 0 ? EYES_HAPPY : reaction < 0 ? EYES_UNHAPPY : EYES_OPEN;
    }

    /** The mouth frame index for the current mood reaction. */
    public static int mouthFrame(LivingEntity entity) {
        int reaction = reactionSign(entity);
        return reaction > 0 ? MOUTH_HAPPY : reaction < 0 ? MOUTH_UNHAPPY : MOUTH_NEUTRAL;
    }

    /** Eyes shut: asleep, or in the brief window of a blink. */
    public static boolean closed(LivingEntity entity) {
        return entity.isSleeping() || blinking(entity);
    }

    /** +1 smile / -1 frown / 0 neutral — non-zero only during the brief window after a mood change. */
    public static int reactionSign(LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA villager)) return 0;
        int mood;
        try {
            mood = villager.getVillagerBrain().getMoodValue();
        } catch (Throwable t) {
            return 0;
        }
        long now = entity.tickCount;
        Reaction r = REACTIONS.get(entity.getId());
        if (r == null) {
            REACTIONS.put(entity.getId(), new Reaction(mood, 0, 0));   // first sight: no reaction
            return 0;
        }
        if (mood != r.lastMood()) {   // mood just shifted: start a reaction in its direction
            r = new Reaction(mood, now + REACTION_TICKS, mood > r.lastMood() ? 1 : -1);
            REACTIONS.put(entity.getId(), r);
        }
        return now < r.untilTick() ? r.sign() : 0;
    }

    /** A brief, per-entity-phased blink so a crowd doesn't blink in unison. */
    private static boolean blinking(LivingEntity entity) {
        long phase = entity.getUUID().getLeastSignificantBits();
        int period = 70 + (int) Math.floorMod(phase, 71);   // 70..140 ticks
        return Math.floorMod(entity.tickCount + Math.floorMod(phase, period), period) < 3;
    }
}
