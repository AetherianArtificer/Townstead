package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.profession.def.ClothingChoice;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ProfessionPaths;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authored profession clothing, keyed by villager profession id, for the client's render fallbacks. */
//? if neoforge {
public record ProfessionClothingSyncPayload(Map<ResourceLocation, Chains> professions) implements CustomPacketPayload {
//?} else {
/*public record ProfessionClothingSyncPayload(Map<ResourceLocation, Chains> professions) {
*///?}
    /** {@code outfit} is the order to wear; {@code every} also holds each Path's clothing, for hair lookups. */
    public record Chains(List<ClothingChoice> outfit, List<ClothingChoice> every) {}

    private static volatile Map<ResourceLocation, Chains> client = Map.of();

    //? if neoforge {
    public static final Type<ProfessionClothingSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "profession_clothing_sync"));

    public static final StreamCodec<FriendlyByteBuf, ProfessionClothingSyncPayload> STREAM_CODEC =
            StreamCodec.of(ProfessionClothingSyncPayload::write, ProfessionClothingSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static ProfessionClothingSyncPayload snapshot() {
        Map<ResourceLocation, Chains> out = new LinkedHashMap<>();
        ProfessionDefs.all().forEach((id, def) -> put(out, id, def, null));
        ProfessionDefs.compatibility().forEach((id, resolution) ->
                put(out, id, ProfessionDefs.all().get(resolution.professionId()), resolution.pathId()));
        return new ProfessionClothingSyncPayload(out);
    }

    private static void put(Map<ResourceLocation, Chains> out, ResourceLocation id, ProfessionDef def, String pathId) {
        if (def == null) return;
        ProfessionPaths.Path implied = pathId == null ? null : ProfessionPaths.byId(def.id(), pathId);
        List<ClothingChoice> outfit = implied != null && !implied.clothing().isEmpty() ? implied.clothing() : def.clothing();
        Set<ClothingChoice> every = new LinkedHashSet<>(outfit);
        every.addAll(def.clothing());
        for (ProfessionPaths.Path path : ProfessionPaths.pathsFor(def.id())) every.addAll(path.clothing());
        if (!every.isEmpty()) out.put(id, new Chains(List.copyOf(outfit), List.copyOf(every)));
    }

    public static void write(FriendlyByteBuf buf, ProfessionClothingSyncPayload payload) {
        buf.writeVarInt(payload.professions.size());
        payload.professions.forEach((id, chains) -> {
            buf.writeResourceLocation(id);
            writeChoices(buf, chains.outfit);
            writeChoices(buf, chains.every);
        });
    }

    public static ProfessionClothingSyncPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<ResourceLocation, Chains> professions = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            List<ClothingChoice> outfit = readChoices(buf);
            professions.put(id, new Chains(outfit, readChoices(buf)));
        }
        return new ProfessionClothingSyncPayload(professions);
    }

    private static void writeChoices(FriendlyByteBuf buf, List<ClothingChoice> choices) {
        buf.writeVarInt(choices.size());
        for (ClothingChoice choice : choices) {
            buf.writeResourceLocation(choice.id());
            buf.writeEnum(choice.hair());
        }
    }

    private static List<ClothingChoice> readChoices(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<ClothingChoice> choices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            choices.add(new ClothingChoice(buf.readResourceLocation(), buf.readEnum(ClothingChoice.HairPolicy.class)));
        }
        return List.copyOf(choices);
    }

    /** Client side. */
    public static void accept(ProfessionClothingSyncPayload payload) {
        client = payload.professions;
    }

    /** Client side: the clothing a villager of this profession should fall back to. */
    public static List<ClothingChoice> outfit(ResourceLocation professionId) {
        Chains chains = professionId == null ? null : client.get(professionId);
        return chains == null ? List.of() : chains.outfit;
    }

    /** Client side: all clothing authored for this profession and its Paths. */
    public static List<ClothingChoice> every(ResourceLocation professionId) {
        Chains chains = professionId == null ? null : client.get(professionId);
        return chains == null ? List.of() : chains.every;
    }
}
