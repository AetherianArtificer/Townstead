package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.sound.SoundSpec;
import com.google.gson.JsonObject;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/** Plays a SoundSpec at the focused block. */
public final class PlaySoundBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:play_sound";

    @Override public String key() { return KEY; }

    @Override public BlockAction parse(JsonObject json) {
        SoundSpec sound = SoundSpec.read(json);
        if (sound == null) return null;
        SoundSource category;
        try {
            category = SoundSource.valueOf(GsonHelper.getAsString(json, "category", "neutral")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            category = SoundSource.NEUTRAL;
        }
        SoundSource source = category;
        return context -> sound.playAt(context.level(), context.pos().getX() + 0.5,
                context.pos().getY() + 0.5, context.pos().getZ() + 0.5,
                source, context.level().random);
    }
}
