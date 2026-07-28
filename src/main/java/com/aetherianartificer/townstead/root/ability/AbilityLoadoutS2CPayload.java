package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the player's resolved ability slots, for the wheel to draw.
 *
 * <p>Resolved rather than raw. The client cannot run {@code slottables()} — that walks the power
 * layer, which is server state — and it has no way to know a cooldown, so it is told the answer
 * instead of the ingredients. Names arrive pre-rendered, matching how the career screen already
 * ships its strings.</p>
 *
 * <p>{@code readyAt} is a game time, not a remaining duration, so a wheel held open for five seconds
 * keeps counting down without another packet.</p>
 */
//? if neoforge {
public record AbilityLoadoutS2CPayload(List<Entry> entries, List<Option> available)
        implements CustomPacketPayload {
//?} else {
/*public record AbilityLoadoutS2CPayload(List<Entry> entries, List<Option> available) {
*///?}

    /**
     * One filled slot. Empty slots are simply absent.
     *
     * <p>{@code kind} is an {@code Assignable.Kind} ordinal, and it is here so the client can tell
     * a press it must perform ITSELF from one it asks the server for. A keybind cannot be pressed
     * on a server; {@code clientValue} carries the binding to press, and is empty for everything
     * the server handles.</p>
     */
    public record Entry(int slot, String id, String name, String icon, boolean toggle,
                        boolean toggledOn, int cooldownTicks, long readyAt,
                        int costAmount, String costLabel, int kind, String clientValue,
                        String source, int costColor, int costHave) {}

    /**
     * Something the player owns and could prepare, whether or not it is in a slot.
     *
     * <p>{@code source} is what it came from — a career's name, or Root — so the picker can group
     * hundreds of abilities into something a person can find one in.</p>
     */
    public record Option(String id, String name, String icon, String source, boolean toggle,
                         int cooldownTicks, int costAmount, String costLabel, int kind,
                         int costColor, int costHave) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeVarInt(entry.slot());
            buf.writeUtf(entry.id());
            buf.writeUtf(entry.name());
            buf.writeUtf(entry.icon());
            buf.writeBoolean(entry.toggle());
            buf.writeBoolean(entry.toggledOn());
            buf.writeVarInt(entry.cooldownTicks());
            buf.writeLong(entry.readyAt());
            buf.writeVarInt(entry.costAmount());
            buf.writeUtf(entry.costLabel());
            buf.writeVarInt(entry.kind());
            buf.writeUtf(entry.clientValue());
            buf.writeUtf(entry.source());
            buf.writeInt(entry.costColor());
            buf.writeVarInt(entry.costHave());
        }
        buf.writeVarInt(available.size());
        for (Option option : available) {
            buf.writeUtf(option.id());
            buf.writeUtf(option.name());
            buf.writeUtf(option.icon());
            buf.writeUtf(option.source());
            buf.writeBoolean(option.toggle());
            buf.writeVarInt(option.cooldownTicks());
            buf.writeVarInt(option.costAmount());
            buf.writeUtf(option.costLabel());
            buf.writeVarInt(option.kind());
            buf.writeInt(option.costColor());
            buf.writeVarInt(option.costHave());
        }
    }

    public static AbilityLoadoutS2CPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readLong(),
                    buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readUtf(),
                    buf.readInt(), buf.readVarInt()));
        }
        int optionCount = buf.readVarInt();
        List<Option> available = new ArrayList<>(optionCount);
        for (int i = 0; i < optionCount; i++) {
            available.add(new Option(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                    buf.readVarInt(), buf.readInt(), buf.readVarInt()));
        }
        return new AbilityLoadoutS2CPayload(List.copyOf(entries), List.copyOf(available));
    }

    //? if neoforge {
    public static final Type<AbilityLoadoutS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "ability_loadout_s2c"));

    public static final StreamCodec<FriendlyByteBuf, AbilityLoadoutS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AbilityLoadoutS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}
