package com.aetherianartificer.townstead.seal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A player's own seal: a device and the dye of its ink. It is pressed wherever the player puts
 * their name to something, a Charter amendment or their Career record, so the same mark means the
 * same person everywhere.
 *
 * <p>{@code device} is a built-in device id, or a resource pack's seal art under
 * {@code textures/stamps/seal/} or {@code textures/stamps/career/}.</p>
 */
public record PersonalSeal(String device, int dye) {
    public static final String INITIAL = "townstead:initial";
    public static final List<String> BUILT_IN = List.of(INITIAL, "townstead:star", "townstead:key",
            "townstead:tower", "townstead:crown", "townstead:tree", "townstead:moon", "townstead:anchor");
    /** Red ink, pressing the owner's initial. */
    public static final PersonalSeal DEFAULT = new PersonalSeal(INITIAL, 14);

    /** The seal as given, or null when its device or dye is not one a seal can carry. */
    public static @Nullable PersonalSeal sanitized(String device, int dye) {
        if (device == null || dye < 0 || dye > 15) return null;
        if (BUILT_IN.contains(device) || art(device)) return new PersonalSeal(device, dye);
        return null;
    }

    public static boolean art(String device) {
        if (device.length() > 200) return false;
        int colon = device.indexOf(':');
        if (colon <= 0) return false;
        String path = device.substring(colon + 1);
        return (path.startsWith("textures/stamps/seal/") || path.startsWith("textures/stamps/career/"))
                && path.endsWith(".png") && !path.contains("..");
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("device", device);
        tag.putInt("dye", dye);
        return tag;
    }

    public static PersonalSeal fromTag(CompoundTag tag) {
        PersonalSeal seal = sanitized(tag.getString("device"), tag.getInt("dye"));
        return seal == null ? DEFAULT : seal;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(device, 200);
        buf.writeVarInt(dye);
    }

    public static PersonalSeal read(FriendlyByteBuf buf) {
        PersonalSeal seal = sanitized(buf.readUtf(200), buf.readVarInt());
        return seal == null ? DEFAULT : seal;
    }
}
