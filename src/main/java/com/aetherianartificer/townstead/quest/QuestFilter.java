package com.aetherianartificer.townstead.quest;

import java.util.Locale;
import java.util.Objects;

/** Search/state/provider filter used by both the screen and headless tests. */
public record QuestFilter(QuestState state, String providerId, String search, java.util.Set<String> hiddenProviders) {
    public static final String ALL_PROVIDERS = "*";

    public QuestFilter {
        state = Objects.requireNonNullElse(state, QuestState.ACTIVE);
        providerId = providerId == null || providerId.isBlank() ? ALL_PROVIDERS : providerId;
        search = Objects.requireNonNullElse(search, "").strip().toLowerCase(Locale.ROOT);
        hiddenProviders = hiddenProviders == null ? java.util.Set.of() : java.util.Set.copyOf(hiddenProviders);
    }

    /** One source, or {@link #ALL_PROVIDERS}. */
    public QuestFilter(QuestState state, String providerId, String search) {
        this(state, providerId, search, java.util.Set.of());
    }

    /** Every source except the hidden ones, so a source installed later shows until unchecked. */
    public static QuestFilter hiding(QuestState state, java.util.Set<String> hiddenProviders, String search) {
        return new QuestFilter(state, ALL_PROVIDERS, search, hiddenProviders);
    }

    public boolean matches(QuestEntry quest) {
        if (quest.state() != state) return false;
        if (!ALL_PROVIDERS.equals(providerId) && !providerId.equals(quest.providerId())) return false;
        if (hiddenProviders.contains(quest.providerId())) return false;
        if (search.isEmpty()) return true;
        return contains(quest.title()) || contains(quest.description()) || contains(quest.group())
                || contains(quest.providerName()) || contains(quest.id())
                || quest.objectives().stream().anyMatch(o -> contains(o.label()))
                || quest.rewards().stream().anyMatch(r -> contains(r.label()));
    }

    private boolean contains(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }
}
