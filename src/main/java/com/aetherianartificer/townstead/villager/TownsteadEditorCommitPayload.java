package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> server commit for Townstead-owned fields edited inside MCA's
 * villager editor. Newer MCA versions sanitize editor NBT patches, so these
 * values need their own transport; the old piggyback NBT path remains as a
 * fallback for older MCA builds.
 */
//? if neoforge {
public record TownsteadEditorCommitPayload(
//?} else {
/*public record TownsteadEditorCommitPayload(
*///?}
        UUID villagerUuid,
        boolean hasHunger,
        int hunger,
        float saturation,
        float hungerExhaustion,
        boolean hasThirst,
        int thirst,
        int quenched,
        float thirstExhaustion,
        boolean hasFatigue,
        int fatigue,
        boolean hasBioAge,
        int bioAgeDays,
        boolean hasBirthday,
        int birthMonth,
        int birthDay,
        boolean hasFamilyName,
        String familyName
//? if neoforge {
) implements CustomPacketPayload {
//?} else {
/*) {
*///?}

    //? if neoforge {
    public static final Type<TownsteadEditorCommitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "editor_commit"));

    public static final StreamCodec<FriendlyByteBuf, TownsteadEditorCommitPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), TownsteadEditorCommitPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "editor_commit");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "editor_commit");
    *///?}

    /** Bounded so a name typed into the editor can never bloat the packet. */
    public static final int MAX_NAME_LENGTH = 128;

    public boolean isEmpty() {
        return !hasHunger && !hasThirst && !hasFatigue && !hasBioAge && !hasBirthday && !hasFamilyName;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(villagerUuid);
        buf.writeBoolean(hasHunger);
        if (hasHunger) {
            buf.writeVarInt(hunger);
            buf.writeFloat(saturation);
            buf.writeFloat(hungerExhaustion);
        }
        buf.writeBoolean(hasThirst);
        if (hasThirst) {
            buf.writeVarInt(thirst);
            buf.writeVarInt(quenched);
            buf.writeFloat(thirstExhaustion);
        }
        buf.writeBoolean(hasFatigue);
        if (hasFatigue) {
            buf.writeVarInt(fatigue);
        }
        buf.writeBoolean(hasBioAge);
        if (hasBioAge) {
            buf.writeVarInt(bioAgeDays);
        }
        buf.writeBoolean(hasBirthday);
        if (hasBirthday) {
            buf.writeVarInt(birthMonth);
            buf.writeVarInt(birthDay);
        }
        buf.writeBoolean(hasFamilyName);
        if (hasFamilyName) {
            buf.writeUtf(familyName == null ? "" : familyName, MAX_NAME_LENGTH);
        }
    }

    public static TownsteadEditorCommitPayload read(FriendlyByteBuf buf) {
        UUID villagerUuid = buf.readUUID();
        boolean hasHunger = buf.readBoolean();
        int hunger = 0;
        float saturation = 0.0f;
        float hungerExhaustion = 0.0f;
        if (hasHunger) {
            hunger = buf.readVarInt();
            saturation = buf.readFloat();
            hungerExhaustion = buf.readFloat();
        }
        boolean hasThirst = buf.readBoolean();
        int thirst = 0;
        int quenched = 0;
        float thirstExhaustion = 0.0f;
        if (hasThirst) {
            thirst = buf.readVarInt();
            quenched = buf.readVarInt();
            thirstExhaustion = buf.readFloat();
        }
        boolean hasFatigue = buf.readBoolean();
        int fatigue = 0;
        if (hasFatigue) {
            fatigue = buf.readVarInt();
        }
        boolean hasBioAge = buf.readBoolean();
        int bioAgeDays = 0;
        if (hasBioAge) {
            bioAgeDays = buf.readVarInt();
        }
        boolean hasBirthday = buf.readBoolean();
        int birthMonth = 0;
        int birthDay = 0;
        if (hasBirthday) {
            birthMonth = buf.readVarInt();
            birthDay = buf.readVarInt();
        }
        boolean hasFamilyName = buf.readBoolean();
        String familyName = "";
        if (hasFamilyName) {
            familyName = buf.readUtf(MAX_NAME_LENGTH);
        }
        return new TownsteadEditorCommitPayload(villagerUuid, hasHunger, hunger, saturation, hungerExhaustion,
                hasThirst, thirst, quenched, thirstExhaustion, hasFatigue, fatigue, hasBioAge, bioAgeDays,
                hasBirthday, birthMonth, birthDay, hasFamilyName, familyName);
    }
}
