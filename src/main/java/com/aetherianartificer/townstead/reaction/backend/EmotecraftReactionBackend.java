package com.aetherianartificer.townstead.reaction.backend;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.emote.AiEmoteScheduler;
import com.aetherianartificer.townstead.reaction.ReactionBinding;
import com.aetherianartificer.townstead.reaction.ReactionContext;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves {@code emotecraft:<Name>} refs through Townstead's existing
 * emote pipeline. Picks one ref uniformly when multiple are listed,
 * lowercases the name for {@link ResourceLocation} compatibility, and
 * ships via {@link AiEmoteScheduler} along with the binding's
 * {@code allow_movement} and {@code parts_skip} so the client adapter
 * can render the emote in mobile mode.
 */
public final class EmotecraftReactionBackend implements ReactionBackend {
    public static final String KEY = "emotecraft";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Optional<String> play(ServerLevel level, LivingEntity villager, ReactionBinding binding,
            ReactionContext context) {
        if (binding == null || binding.refIds().isEmpty() || villager == null || level == null) {
            return Optional.empty();
        }
        String chosen = binding.refIds().size() == 1 ? binding.refIds().get(0)
                : binding.refIds().get(level.getRandom().nextInt(binding.refIds().size()));
        String lowercase = chosen.toLowerCase(Locale.ROOT);
        ResourceLocation rl = ResourceLocation.tryParse(Townstead.MOD_ID + ":" + lowercase);
        if (rl == null) {
            Townstead.LOGGER.warn("Reaction emotecraft ref '{}' could not be normalized to a ResourceLocation", chosen);
            return Optional.empty();
        }
        byte loopOverride = (byte) -1;
        float speed = 1.0F;
        if (binding.args().isPresent()) {
            JsonObject obj = binding.args().get();
            loopOverride = (byte) GsonHelper.getAsInt(obj, "loop_override", -1);
            float parsedSpeed = GsonHelper.getAsFloat(obj, "speed", 1.0F);
            if (parsedSpeed > 0F && Float.isFinite(parsedSpeed)) speed = parsedSpeed;
        }
        String skipped = String.join(",", binding.partsSkip());
        AiEmoteScheduler.playEmote(villager, rl, loopOverride, speed, binding.allowMovement(), skipped);
        return Optional.of(lowercase);
    }
}
