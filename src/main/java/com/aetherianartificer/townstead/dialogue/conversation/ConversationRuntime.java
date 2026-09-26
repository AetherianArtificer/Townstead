package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import com.aetherianartificer.townstead.culture.CultureAssignment;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.dialogue.conversation.generative.*;
import com.aetherianartificer.townstead.hangout.*;
import com.aetherianartificer.townstead.root.LifeStage;
import com.aetherianartificer.townstead.root.LifeStageProgression;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The world-facing half of generative conversations: speaker facts, presence, recency and rendering. */
final class ConversationRuntime {
    private ConversationRuntime() {}

    static final String ENGLISH = "en_us";

    // ---- speakers -------------------------------------------------------------------------------

    static LineComposer.Speaker speaker(ServerLevel level, VillagerEntityMCA villager, GenerativeDialogue.Data data,
                                        String register, Set<String> facts) {
        String stage, stageId;
        LifeStage current = LifeStageProgression.currentStage(villager);
        if (current != null) {
            stage = current.presentsAs().name().toLowerCase(Locale.ROOT);
            stageId = current.id();
        } else {
            stage = villager.getAgeState().name().toLowerCase(Locale.ROOT);
            stageId = stage;
        }
        if ("baby".equals(stage) || "toddler".equals(stage)) stage = "child";
        String personality = McaPersonalityCompat.id(villager.getVillagerBrain().getPersonality());
        if (personality.startsWith("mca:")) personality = personality.substring(4);
        List<ResourceLocation> voices = data.voiceChain(culture(level, villager));
        DialogueVoice own = data.voices().get(voices.get(0));
        double target = own == null ? 0 : own.target(register, stage, stageId, facts);
        DialogueVoice.Children children = own == null ? DialogueVoice.Children.SIMPLE : own.children();
        return new LineComposer.Speaker(villager.getUUID(), stage, stageId, personality, voices, children, target);
    }

    /** The recorded culture. Spawn and the villager tick settle cultures; conversation only reads. */
    static String culture(ServerLevel level, VillagerEntityMCA villager) {
        var culture = CultureAssignment.recorded(villager);
        return culture == null ? "" : culture.id().toString();
    }

    static String stage(VillagerEntityMCA villager) {
        LifeStage current = LifeStageProgression.currentStage(villager);
        return current != null ? current.presentsAs().name().toLowerCase(Locale.ROOT)
                : villager.getAgeState().name().toLowerCase(Locale.ROOT);
    }

    // ---- presence -------------------------------------------------------------------------------

    /** Presence requirements checked in the scope they name. Unknown scopes fail closed. */
    static final class Presence {
        private final ServerLevel level;
        private final VillagerEntityMCA speaker;
        private final GenerativeDialogue.Data data;
        private Set<String> hereTypes, hereTags, homeTags, hereDecorations;
        /** How far past the village buildings a decoration still counts as part of the village. */
        private static final int DECORATION_MARGIN = 16;

        Presence(ServerLevel level, VillagerEntityMCA speaker, GenerativeDialogue.Data data) {
            this.level = level;
            this.speaker = speaker;
            this.data = data;
        }

        boolean test(String requirement) {
            if (requirement.startsWith("!")) return !test(requirement.substring(1));
            int colon = requirement.indexOf(':');
            String scope = colon < 0 ? requirement : requirement.substring(0, colon);
            String value = colon < 0 ? "" : requirement.substring(colon + 1);
            return switch (scope) {
                case "here.building", "subject.building" -> here().contains(value);
                case "here.building_tag", "subject.building_tag" -> hereTags().contains(value);
                case "home.building_tag" -> homeTags().contains(value);
                case "here.decoration", "subject.decoration" -> hereDecorations().contains(value);
                case "here.biome_tag" -> biomeTag(value);
                case "mod" -> ModCompat.isLoaded(value);
                case "time" -> value.equals(night() ? "night" : "day");
                case "venue.amenity" -> venue() != null && venue().amenities().contains(value);
                case "venue.tag" -> venue() != null && venue().tags().stream()
                        .anyMatch(tag -> tag.toString().equals(value) || tag.getPath().equals(value));
                default -> false;
            };
        }

        /** From dusk until dawn. */
        private boolean night() {
            long time = Math.floorMod(level.getDayTime(), 24000L);
            return time >= 12500 && time < 23500;
        }

        /** The venue the speaker is visiting now, or null outside a hangout. */
        private @Nullable HangoutVenue venue() {
            HangoutVisit visit = HangoutEngine.visit(speaker.getUUID());
            return visit == null || visit.phase() != HangoutVisit.Phase.PRESENT ? null : HangoutData.venues().get(visit.venueDefinition());
        }

        private Set<String> here() {
            if (hereTypes == null) hereTypes = types(VillageManager.get(level)
                    .findNearestVillage(speaker.blockPosition(), Village.MERGE_MARGIN).orElse(null));
            return hereTypes;
        }

        /** Decoration types, such as {@code townstead:well}, anywhere in the area of the village the speaker is in. */
        private Set<String> hereDecorations() {
            if (hereDecorations != null) return hereDecorations;
            Village village = VillageManager.get(level).findNearestVillage(speaker.blockPosition(), Village.MERGE_MARGIN).orElse(null);
            Set<String> out = new HashSet<>();
            if (village != null) {
                var box = village.getBox();
                int radius = Math.max(box.getXSpan(), box.getZSpan()) / 2 + DECORATION_MARGIN;
                for (var decoration : com.aetherianartificer.townstead.decoration.DecorationSavedData.get(level)
                        .within(new net.minecraft.core.BlockPos(village.getCenter()), radius))
                    out.add(decoration.decorationId().toString());
            }
            hereDecorations = Set.copyOf(out);
            return hereDecorations;
        }

        private Set<String> hereTags() {
            if (hereTags == null) hereTags = tags(here());
            return hereTags;
        }

        private Set<String> homeTags() {
            if (homeTags == null) homeTags = tags(types(speaker.getResidency().getHomeVillage().orElse(null)));
            return homeTags;
        }

        private Set<String> types(@Nullable Village village) {
            if (village == null) return Set.of();
            try {
                return Set.copyOf(McaBuildingCompat.effectiveTypes(village).values());
            } catch (RuntimeException ex) {
                return Set.of();
            }
        }

        private Set<String> tags(Set<String> types) {
            Set<String> out = new HashSet<>();
            for (String type : types) {
                BuildingTalk talk = data.buildingTalk().get(type);
                if (talk != null) out.addAll(talk.tags());
            }
            return out;
        }

        private boolean biomeTag(String value) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) return false;
            return level.getBiome(speaker.blockPosition()).is(TagKey.create(Registries.BIOME, id));
        }
    }

    // ---- recency --------------------------------------------------------------------------------

    /** Parts heard recently in one village. Shared by every speaker there, because a player hears all of them. */
    static final class VillageRecency implements LineComposer.Recency {
        private final Map<ResourceLocation, Set<String>> used = new HashMap<>();

        @Override public boolean used(ResourceLocation pool, String key) {
            return used.getOrDefault(pool, Set.of()).contains(key);
        }
        @Override public void use(ResourceLocation pool, String key) {
            used.computeIfAbsent(pool, k -> new HashSet<>()).add(key);
        }
        @Override public void release(ResourceLocation pool, Collection<String> keys) {
            Set<String> set = used.get(pool);
            if (set != null) set.removeAll(keys);
        }
    }

    // ---- rendering ------------------------------------------------------------------------------

    /** A line ready to send. Townstead resolves it on the server in each listener's locale. */
    interface Spoken {
        MutableComponent component(String locale);
        String english();
    }

    private static final Pattern POSITION = Pattern.compile("%(\\d+)\\$s");

    static Spoken render(LineComposer.Line line, @Nullable LineComposer.Subject subject,
                         VillagerEntityMCA speaker, VillagerEntityMCA listener) {
        Map<String, DialogueText.Value> values = new HashMap<>();
        values.put("self", person(speaker.getName().getString(), speaker));
        values.put("other", person(listener.getName().getString(), listener));
        if (subject != null) for (var e : subject.slots().entrySet()) {
            LineComposer.SlotValue slot = e.getValue();
            Map<String, String> meta = new HashMap<>(slot.meta());
            if (slot.person() != null && speaker.level() instanceof ServerLevel level
                    && level.getEntity(slot.person()) instanceof VillagerEntityMCA villager) meta.put("gender", gender(villager));
            values.put(e.getKey(), new DialogueText.Value(slot.english(), slot.key(), Map.copyOf(meta)));
        }
        List<DialoguePart> parts = line.parts();
        String frameKey = line.frame().key() != null ? line.frame().key() : "townstead.frame." + parts.size();
        String english = text(parts, frameKey, values, ENGLISH);
        return new Spoken() {
            @Override public MutableComponent component(String locale) {
                return Component.literal(ENGLISH.equals(locale) ? english : text(parts, frameKey, values, locale));
            }
            @Override public String english() { return english; }
        };
    }

    /** An authored set-piece line. A key with no data-pack text is left for the client to translate. */
    static Spoken renderKey(String key) {
        String english = DataPackLang.find(key, ENGLISH);
        return new Spoken() {
            @Override public MutableComponent component(String locale) {
                String found = DataPackLang.find(key, locale);
                return found != null ? Component.literal(found) : Component.translatable(key);
            }
            @Override public String english() { return english == null ? key : english; }
        };
    }

    /** Sends a line to every player in chat range, each in their own locale, the way MCA sends villager chat. */
    static void send(VillagerEntityMCA speaker, Spoken spoken) {
        for (ServerPlayer player : speaker.level().getEntitiesOfClass(ServerPlayer.class, speaker.getBoundingBox().inflate(20))) {
            float distance = player.distanceTo(speaker);
            speaker.sendChatMessage(spoken.component(locale(player))
                    .withStyle(distance < 10 ? ChatFormatting.WHITE : ChatFormatting.GRAY), player);
        }
    }

    /** Whether any player is in chat range of this speaker. */
    static boolean audible(VillagerEntityMCA speaker) {
        return !speaker.level().getEntitiesOfClass(ServerPlayer.class, speaker.getBoundingBox().inflate(20)).isEmpty();
    }

    static String locale(ServerPlayer player) {
        //? if >=1.21 {
        return player.clientInformation().language();
        //?} else {
        /*return player.getLanguage();
        *///?}
    }

    private static String text(List<DialoguePart> parts, String frameKey, Map<String, DialogueText.Value> values, String locale) {
        List<String> filled = new ArrayList<>();
        for (DialoguePart part : parts) {
            String template = DataPackLang.find(part.key(), locale);
            filled.add(DialogueText.capitalize(DialogueText.fill(template != null ? template : part.english(), values, locale)));
        }
        String frame = DataPackLang.find(frameKey, locale);
        if (frame == null) return String.join(" ", filled);
        Matcher m = POSITION.matcher(frame);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int index = Integer.parseInt(m.group(1)) - 1;
            m.appendReplacement(out, Matcher.quoteReplacement(index >= 0 && index < filled.size() ? filled.get(index) : ""));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static DialogueText.Value person(String name, VillagerEntityMCA villager) {
        return new DialogueText.Value(name, null, Map.of("gender", gender(villager)));
    }

    /** m, f or n, for word forms that agree with a person. */
    static String gender(VillagerEntityMCA villager) {
        try {
            return switch (villager.getGenetics().getGender().name()) {
                case "MALE" -> "m";
                case "FEMALE" -> "f";
                default -> "n";
            };
        } catch (RuntimeException ex) {
            return "n";
        }
    }

    /** Line duration from its length: long enough to read, short enough to feel like talk. */
    static int duration(String english, int authored) {
        if (authored > 0) return authored;
        return Math.max(40, Math.min(160, 30 + (int) (english.length() * 2.2)));
    }
}
