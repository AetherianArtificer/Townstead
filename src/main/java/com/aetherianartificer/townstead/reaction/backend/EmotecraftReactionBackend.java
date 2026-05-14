package com.aetherianartificer.townstead.reaction.backend;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.emote.AiEmoteScheduler;
import com.aetherianartificer.townstead.reaction.ReactionContext;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves {@code emotecraft:<Name>} refs to Townstead's existing emote
 * pipeline. Picks one ref uniformly when multiple variants are listed,
 * lowercases the name to fit Minecraft {@link ResourceLocation} path
 * rules, builds a placeholder RL with the {@code townstead} namespace,
 * and ships via {@link AiEmoteScheduler}. The client's emote handler is
 * responsible for resolving the path case-insensitively against whatever
 * namespace the actual emote file lives under.
 */
public final class EmotecraftReactionBackend implements ReactionBackend {
    public static final String KEY = "emotecraft";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Optional<String> play(ServerLevel level, LivingEntity villager, List<String> refIds,
            Optional<JsonObject> args, ReactionContext context) {
        if (refIds == null || refIds.isEmpty() || villager == null || level == null) return Optional.empty();
        String chosen = refIds.size() == 1 ? refIds.get(0)
                : refIds.get(level.getRandom().nextInt(refIds.size()));
        String lowercase = chosen.toLowerCase(Locale.ROOT);
        ResourceLocation rl = ResourceLocation.tryParse(Townstead.MOD_ID + ":" + lowercase);
        if (rl == null) {
            Townstead.LOGGER.warn("Reaction emotecraft ref '{}' could not be normalized to a ResourceLocation", chosen);
            return Optional.empty();
        }
        byte loopOverride = (byte) -1;
        float speed = 1.0F;
        if (args.isPresent()) {
            JsonObject obj = args.get();
            loopOverride = (byte) GsonHelper.getAsInt(obj, "loop_override", -1);
            float parsedSpeed = GsonHelper.getAsFloat(obj, "speed", 1.0F);
            if (parsedSpeed > 0F && Float.isFinite(parsedSpeed)) speed = parsedSpeed;
        }
        AiEmoteScheduler.playEmote(villager, rl, loopOverride, speed);
        return Optional.of(lowercase);
    }
}
