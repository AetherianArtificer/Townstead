package com.aetherianartificer.townstead.emote;

import java.util.Set;
import java.util.UUID;

public record ActivePlayerEmote(UUID uuid, Set<String> aliases) {
}
