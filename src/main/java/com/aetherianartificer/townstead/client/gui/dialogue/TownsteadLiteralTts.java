package com.aetherianartificer.townstead.client.gui.dialogue;

import net.conczin.mca.Config;
import net.conczin.mca.client.tts.AudioCache;
import net.conczin.mca.client.tts.ElevenlabsSpeechManager;
import net.conczin.mca.client.tts.OnlineSpeechManager;
import net.conczin.mca.client.tts.Player2SpeechManager;
import net.conczin.mca.client.tts.RealtimeSpeechManager;
import net.conczin.mca.client.tts.SpeechManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class TownsteadLiteralTts {
    private static final RealtimeSpeechManager REALTIME = new RealtimeSpeechManager(Config.getInstance().onlineTTSServer);
    private static final Player2SpeechManager PLAYER2 = new Player2SpeechManager(Config.getInstance().player2Url);
    private static final ElevenlabsSpeechManager ELEVENLABS = new ElevenlabsSpeechManager();

    private TownsteadLiteralTts() {
    }

    static boolean canSpeakCurrentMode() {
        if (!Config.getInstance().enableOnlineTTS) return false;
        return switch (Config.getInstance().onlineTTSModel) {
            case "realtime", "player2", "elevenlabs" -> true;
            default -> false;
        };
    }

    static void speak(String text, VillagerEntityMCA villager) {
        if (!canSpeakCurrentMode()) return;
        if (text == null || text.isBlank()) return;
        if (villager.isSpeechImpaired() || villager.isToYoungToSpeak()) return;

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
            default -> {
            }
        }
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
