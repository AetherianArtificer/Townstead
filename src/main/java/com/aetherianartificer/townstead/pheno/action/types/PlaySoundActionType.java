package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.sound.SoundSpec;
import com.google.gson.JsonObject;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * Plays a sound at the actor (Apoli's {@code play_sound}). The sound is a {@link SoundSpec},
 * so it accepts a single id, a {@code {sound,volume,pitch}} object, or a weighted
 * {@code "sounds"} array (Apugli's {@code weighted_sound_event}).
 *
 * <p>JSON: {@code { "type":"pheno:play_sound", "sound":"minecraft:entity.player.levelup",
 * "volume":1.0, "pitch":1.0 }} or {@code { "type":"pheno:play_sound",
 * "sounds":[ { "sound":"minecraft:entity.cat.purr", "weight":3 }, { "sound":"minecraft:entity.cat.ambient" } ] }}</p>
 */
public final class PlaySoundActionType implements ActionType {

    public static final String KEY = "pheno:play_sound";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        SoundSpec spec = SoundSpec.read(json);
        if (spec == null) return null;
        SoundSource source = parseSource(GsonHelper.getAsString(json, "category", "neutral"));
        return ctx -> spec.playAt(ctx.entity().level(), ctx.entity().getX(), ctx.entity().getY(),
                ctx.entity().getZ(), source, ctx.entity().getRandom());
    }

    private static SoundSource parseSource(String raw) {
        try {
            return SoundSource.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SoundSource.NEUTRAL;
        }
    }
}
