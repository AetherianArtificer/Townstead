package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.CultureAssignment;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.dialogue.conversation.generative.DialogueText;
import com.aetherianartificer.townstead.dialogue.conversation.generative.LineComposer;
import com.aetherianartificer.townstead.root.Demonym;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demonym placeholders every line may use: {@code {self_demonym}}, {@code {self_demonym_plural}} and
 * {@code {self_demonym_adj}}, the same for {@code other}, and for {@code home}, the speaker's village.
 * A slot exists only when that culture has a demonym, so a line that needs one is skipped otherwise.
 * A person's own {@code _demonym} agrees with their gender in locales that define the forms.
 */
public final class CultureSlots {
    private CultureSlots() {}

    private static final List<String> OWNERS = List.of("self", "other", "home");
    private static final List<String> FORMS = List.of("demonym", "demonym_plural", "demonym_adj");

    /** Every slot name this class can supply. */
    public static final Set<String> NAMES;
    static {
        Set<String> names = new LinkedHashSet<>();
        for (String owner : OWNERS) for (String form : FORMS) names.add(owner + "_" + form);
        NAMES = Set.copyOf(names);
    }

    public static Map<String, LineComposer.SlotValue> of(ServerLevel level, VillagerEntityMCA speaker,
                                                          VillagerEntityMCA listener) {
        Map<String, LineComposer.SlotValue> out = new HashMap<>();
        put(out, "self", CultureAssignment.recorded(speaker), ConversationRuntime.gender(speaker));
        put(out, "other", CultureAssignment.recorded(listener), ConversationRuntime.gender(listener));
        put(out, "home", Cultures.get(CultureAssignment.ofVillage(level, speaker)), null);
        return out;
    }

    private static void put(Map<String, LineComposer.SlotValue> out, String owner, @Nullable Culture culture,
                            @Nullable String gender) {
        Demonym demonym = culture == null ? null : culture.demonym();
        if (demonym == null) return;
        out.put(owner + "_demonym", slot(demonym.singular(), gender));
        out.put(owner + "_demonym_plural", slot(demonym.plural(), null));
        out.put(owner + "_demonym_adj", slot(demonym.adjectiveOrSingular(), null));
    }

    private static LineComposer.SlotValue slot(Component text, @Nullable String agree) {
        String key = text.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
        return new LineComposer.SlotValue(text.getString(), key, null,
                agree == null ? Map.of() : Map.of(DialogueText.AGREE, agree, "gender", agree));
    }
}
