package com.aetherianartificer.townstead.root.personality;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A data-pack-defined personality that reskins a base MCA personality. {@code base} is an MCA
 * personality name or namespaced id (case-insensitive for legacy names, e.g. {@code odd}) it extends: the bearer's MCA
 * personality is set to that base so all of MCA's mechanics (chat success/heart/gift effects) apply
 * unchanged, while this definition supplies the display name and a voice tier. Loaded from
 * {@code data/<ns>/personalities/<id>.json}.
 */
public record PersonalityDef(ResourceLocation id, String base, Component displayName, Component description) {
}
