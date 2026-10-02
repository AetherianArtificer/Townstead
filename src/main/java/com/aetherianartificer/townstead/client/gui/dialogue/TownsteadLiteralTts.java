package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.client.gui.dialogue.effect.EffectTagParser;
import com.aetherianartificer.townstead.client.tts.McaSpeechKeys;
import net.conczin.mca.Config;
import net.conczin.mca.client.tts.AudioCache;
import net.conczin.mca.client.tts.ElevenlabsSpeechManager;
import net.conczin.mca.client.tts.OnlineSpeechManager;
import net.conczin.mca.client.tts.Player2SpeechManager;
import net.conczin.mca.client.tts.RealtimeSpeechManager;
import net.conczin.mca.client.tts.SpeechManager;
import net.conczin.mca.client.tts.resources.OnlineLanguageMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Speaks lines MCA's own TTS will not. Its default backend voices only text it can trace back to a
 * translation key, so a Townstead line resolved on the server arrives as a literal and {@code play}
 * returns at once. The backends are re-entered here with the text as it stands.
 */
public final class TownsteadLiteralTts {
    private static final RealtimeSpeechManager REALTIME = new RealtimeSpeechManager(Config.getInstance().onlineTTSServer);
    private static final Player2SpeechManager PLAYER2 = new Player2SpeechManager(Config.getInstance().player2Url);
    private static final ElevenlabsSpeechManager ELEVENLABS = new ElevenlabsSpeechManager();
    private static final OnlineSpeechManager ONLINE = new OnlineSpeechManager();

    private TownsteadLiteralTts() {
    }

    static boolean canSpeakCurrentMode() {
        // Every model MCA dispatches to now has a literal-capable path here, including the
        // unnamed default it falls through to, so online TTS being on is the whole condition.
        return Config.getInstance().enableOnlineTTS;
    }

    /**
     * Voices a dialogue line the screen is about to show. A line MCA can trace back to a translation
     * key goes to MCA's speech manager, which honours the player's TTS choice including the offline
     * voice packs; only a line MCA cannot name falls through to the literal path here.
     *
     * @param keyedLine   the line as it arrived, whose contents carry the key MCA looks up
     * @param displayLine the same line as the screen shows it, used when speaking literally
     */
    public static void speakLine(Component keyedLine, String displayLine, VillagerEntityMCA villager) {
        if (McaSpeechKeys.isKeyed(keyedLine)) {
            SpeechManager.INSTANCE.onChatMessage(keyedLine, villager.getUUID());
            return;
        }
        speak(displayLine, villager);
    }

    /**
     * Voices a villager's chat line when MCA's own handler would drop it. The other backends speak
     * literals already, and a translation-keyed line is MCA's to say, so both are left alone.
     */
    public static void speakChatLine(Component message, UUID sender) {
        if (!Config.getInstance().enableOnlineTTS) return;
        if (mcaSpeaksLiterals()) return;
        if (McaSpeechKeys.isKeyed(message)) return;

        VillagerEntityMCA villager = findSpeaker(sender);
        if (villager == null) return;
        speak(message.getString(), villager);
    }

    private static boolean mcaSpeaksLiterals() {
        return switch (Config.getInstance().onlineTTSModel) {
            case "realtime", "player2", "elevenlabs" -> true;
            default -> false;
        };
    }

    private static VillagerEntityMCA findSpeaker(UUID sender) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof VillagerEntityMCA villager && entity.getUUID().equals(sender)) {
                return villager;
            }
        }
        return null;
    }

    static void speak(String rawText, VillagerEntityMCA villager) {
        if (!canSpeakCurrentMode()) return;
        if (rawText == null || rawText.isBlank()) return;
        if (villager.isSpeechImpaired() || villager.isToYoungToSpeak()) return;

        // A line still carries its effect markup at this point, and <yell> is not a word.
        String text = EffectTagParser.stripTags(rawText);
        if (text.isBlank()) return;

        String model = Config.getInstance().onlineTTSModel;
        String gender = villager.getGenetics().getGender().binary().getDataName();
        String language = Minecraft.getInstance().options.languageCode;
        float pitch = villager.getVoicePitch();
        float gene = villager.getGenetics().getGene(Genetics.VOICE_TONE);
        String escapedText = escapeJson(text);

        switch (model) {
            case "player2" -> PLAYER2.play(escapedText, gender, language, pitch, gene);
            case "elevenlabs" -> playElevenlabs(escapedText, gender, pitch, gene, villager);
            case "realtime" -> playRealtime(escapedText, gender, language, pitch, gene, villager);
            // The default server takes its text URL-encoded, so it gets the unescaped line.
            default -> playOnline(text, gender, language, pitch, gene, villager);
        }
    }

    /**
     * Mirrors {@code OnlineSpeechManager#play} without its translatable-only gate, and keeps MCA's
     * cache path so a line MCA has already generated is reused rather than fetched twice.
     */
    private static void playOnline(String text, String gender, String gameLang, float pitch, float gene, VillagerEntityMCA villager) {
        String language = OnlineLanguageMap.LANGUAGE_MAP.getOrDefault(gameLang, "");
        if (language.isEmpty()) {
            OnlineSpeechManager.languageNotSupported();
            return;
        }

        String phrase = OnlineSpeechManager.cleanPhrase(text);
        if (phrase.isBlank()) return;
        int tone = Math.min(OnlineSpeechManager.TOTAL_VOICES - 1, (int) Math.floor(gene * OnlineSpeechManager.TOTAL_VOICES));
        String voice = gender + "_" + tone;

        CompletableFuture.runAsync(() -> {
            String cacheKey = language + "-" + voice + "/" + AudioCache.getHash(phrase) + ".ogg";
            if (AudioCache.cachedRetrieve(cacheKey, output -> ONLINE.downloadAudio(output, language, voice, phrase))) {
                playCached(cacheKey, pitch, villager);
            }
        });
    }

    private static void playRealtime(String text, String gender, String language, float pitch, float gene, VillagerEntityMCA villager) {
        CompletableFuture.runAsync(() -> {
            List<String> voices = REALTIME.getVoices(language, gender);
            if (voices == null) return;
            if (voices.isEmpty()) {
                OnlineSpeechManager.languageNotSupported();
                return;
            }

            int tone = Math.min(voices.size() - 1, (int) Math.floor(gene * voices.size()));
            String voice = voices.get(tone);
            String cacheKey = cacheKey("realtime", voice, text);
            if (AudioCache.get(cacheKey, output -> REALTIME.downloadAudio(output, voice, text), true)) {
                playCached(cacheKey, pitch, villager);
            }
        });
    }

    private static void playElevenlabs(String text, String gender, float pitch, float gene, VillagerEntityMCA villager) {
        CompletableFuture.runAsync(() -> {
            List<String> voices = gender.equals("male")
                    ? Config.getInstance().elevenlabsMaleVoices
                    : Config.getInstance().elevenlabsFemaleVoices;
            if (voices.isEmpty()) return;

            int tone = Math.min(voices.size() - 1, (int) Math.floor(gene * voices.size()));
            String voice = voices.get(tone);
            String cacheKey = cacheKey("elevenlabs", voice, text);
            if (AudioCache.get(cacheKey, output -> ELEVENLABS.downloadAudio(output, voice, text), true)) {
                playCached(cacheKey, pitch, villager);
            }
        });
    }

    private static void playCached(String cacheKey, float pitch, VillagerEntityMCA villager) {
        //? if >=1.21 {
        ResourceLocation soundLocation = ResourceLocation.fromNamespaceAndPath("mca", "tts_cache/" + cacheKey);
        //?} else {
        /*ResourceLocation soundLocation = new ResourceLocation("mca", "tts_cache/" + cacheKey);
        *///?}
        SpeechManager.INSTANCE.playSound(pitch, villager, soundLocation);
    }

    private static String cacheKey(String model, String voice, String text) {
        return "townstead/" + model + "/" + AudioCache.getHash(voice) + "/" + AudioCache.getHash(text);
    }

    private static String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
