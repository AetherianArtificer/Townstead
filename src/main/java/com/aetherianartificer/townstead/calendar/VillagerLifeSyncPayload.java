package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client snapshot of one villager's birth date and resolved life
 * stage. Sent when a player starts tracking the villager, when
 * {@link VillagerLifeStamper} fabricates a DOB, and daily for seniors so hair
 * desaturation tracks progress.
 *
 * <p>Like {@link CalendarSyncPayload}, month and stage names travel as
 * (translate key, fallback) pairs for per-client locale resolution. The stage
 * arrays describe the villager's full resolved cycle (per-stage day durations +
 * labels) so the editor can build its age slider and the interact screen can
 * name the current stage without a server round-trip.</p>
 */
public record VillagerLifeSyncPayload(
        int entityId,
        int birthYear,
        int birthMonthIndex,
        int birthDayOfMonth,
        String birthMonthKey,
        String birthMonthFallback,
        int ageYears,
        boolean stamped,
        boolean isSenior,
        int seniorProgressPermil,
        int bioAgeDays,
        boolean immortal,
        boolean ageless,
        int currentStageIndex,
        int[] stageDays,
        String[] stageLabelKeys,
        String[] stageLabelFallbacks,
        float narrativeAge,
        float[] stageScales,
        int[] stageModelAges,
        float[] stageNarrativeMin,
        float[] stageNarrativeMax,
        float narrativeRate,
        int seniorStageIndex,
        String personalityName,
        String personalityDesc,
        String[] personalityPoolRefs,
        String[] personalityPoolNames
//? if neoforge {
) implements CustomPacketPayload {
//?} else {
/*) {
*///?}

    //? if neoforge {
    public static final Type<VillagerLifeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_life_sync"));

    public static final StreamCodec<FriendlyByteBuf, VillagerLifeSyncPayload> STREAM_CODEC = StreamCodec.of(
            VillagerLifeSyncPayload::write,
            VillagerLifeSyncPayload::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_life_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "villager_life_sync");
    *///?}

    /** Copy keyed to a different network id, for re-keying a reply to the editor's preview entity. */
    public VillagerLifeSyncPayload withEntityId(int newEntityId) {
        return new VillagerLifeSyncPayload(newEntityId, birthYear, birthMonthIndex, birthDayOfMonth,
                birthMonthKey, birthMonthFallback, ageYears, stamped, isSenior, seniorProgressPermil,
                bioAgeDays, immortal, ageless, currentStageIndex, stageDays, stageLabelKeys, stageLabelFallbacks,
                narrativeAge, stageScales, stageModelAges, stageNarrativeMin, stageNarrativeMax,
                narrativeRate, seniorStageIndex, personalityName, personalityDesc,
                personalityPoolRefs, personalityPoolNames);
    }

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        write(buf, this);
    }
    *///?}

    public static void write(FriendlyByteBuf buf, VillagerLifeSyncPayload p) {
        buf.writeVarInt(p.entityId());
        buf.writeVarInt(p.birthYear());
        buf.writeVarInt(p.birthMonthIndex());
        buf.writeVarInt(p.birthDayOfMonth());
        buf.writeUtf(p.birthMonthKey());
        buf.writeUtf(p.birthMonthFallback());
        buf.writeVarInt(p.ageYears());
        buf.writeBoolean(p.stamped());
        buf.writeBoolean(p.isSenior());
        buf.writeVarInt(p.seniorProgressPermil());
        buf.writeVarInt(p.bioAgeDays());
        buf.writeBoolean(p.immortal());
        buf.writeBoolean(p.ageless());
        buf.writeVarInt(p.currentStageIndex());
        int[] days = p.stageDays();
        buf.writeVarInt(days.length);
        for (int d : days) buf.writeVarInt(Math.max(0, d));
        String[] keys = p.stageLabelKeys();
        String[] fallbacks = p.stageLabelFallbacks();
        buf.writeVarInt(keys.length);
        for (int i = 0; i < keys.length; i++) {
            buf.writeUtf(keys[i] == null ? "" : keys[i]);
            buf.writeUtf(i < fallbacks.length && fallbacks[i] != null ? fallbacks[i] : "");
        }
        buf.writeFloat(p.narrativeAge());
        float[] scales = p.stageScales();
        buf.writeVarInt(scales == null ? 0 : scales.length);
        if (scales != null) for (float sc : scales) buf.writeFloat(sc);
        int[] modelAges = p.stageModelAges();
        buf.writeVarInt(modelAges == null ? 0 : modelAges.length);
        if (modelAges != null) for (int a : modelAges) buf.writeInt(a);
        float[] narrMin = p.stageNarrativeMin();
        buf.writeVarInt(narrMin == null ? 0 : narrMin.length);
        if (narrMin != null) for (float v : narrMin) buf.writeFloat(v);
        float[] narrMax = p.stageNarrativeMax();
        buf.writeVarInt(narrMax == null ? 0 : narrMax.length);
        if (narrMax != null) for (float v : narrMax) buf.writeFloat(v);
        buf.writeFloat(p.narrativeRate());
        buf.writeInt(p.seniorStageIndex());
        buf.writeUtf(p.personalityName() == null ? "" : p.personalityName());
        buf.writeUtf(p.personalityDesc() == null ? "" : p.personalityDesc());
        String[] refs = p.personalityPoolRefs() == null ? new String[0] : p.personalityPoolRefs();
        String[] names = p.personalityPoolNames() == null ? new String[0] : p.personalityPoolNames();
        buf.writeVarInt(refs.length);
        for (int i = 0; i < refs.length; i++) {
            buf.writeUtf(refs[i] == null ? "" : refs[i]);
            buf.writeUtf(i < names.length && names[i] != null ? names[i] : "");
        }
    }

    public static VillagerLifeSyncPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int birthYear = buf.readVarInt();
        int birthMonthIndex = buf.readVarInt();
        int birthDayOfMonth = buf.readVarInt();
        String birthMonthKey = buf.readUtf();
        String birthMonthFallback = buf.readUtf();
        int ageYears = buf.readVarInt();
        boolean stamped = buf.readBoolean();
        boolean isSenior = buf.readBoolean();
        int seniorProgressPermil = buf.readVarInt();
        int bioAgeDays = buf.readVarInt();
        boolean immortal = buf.readBoolean();
        boolean ageless = buf.readBoolean();
        int currentStageIndex = buf.readVarInt();
        int dayCount = buf.readVarInt();
        int[] stageDays = new int[dayCount];
        for (int i = 0; i < dayCount; i++) stageDays[i] = buf.readVarInt();
        int labelCount = buf.readVarInt();
        String[] keys = new String[labelCount];
        String[] fallbacks = new String[labelCount];
        for (int i = 0; i < labelCount; i++) {
            keys[i] = buf.readUtf();
            fallbacks[i] = buf.readUtf();
        }
        float narrativeAge = buf.readFloat();
        int scaleCount = buf.readVarInt();
        float[] stageScales = new float[scaleCount];
        for (int i = 0; i < scaleCount; i++) stageScales[i] = buf.readFloat();
        int modelAgeCount = buf.readVarInt();
        int[] stageModelAges = new int[modelAgeCount];
        for (int i = 0; i < modelAgeCount; i++) stageModelAges[i] = buf.readInt();
        int narrMinCount = buf.readVarInt();
        float[] stageNarrativeMin = new float[narrMinCount];
        for (int i = 0; i < narrMinCount; i++) stageNarrativeMin[i] = buf.readFloat();
        int narrMaxCount = buf.readVarInt();
        float[] stageNarrativeMax = new float[narrMaxCount];
        for (int i = 0; i < narrMaxCount; i++) stageNarrativeMax[i] = buf.readFloat();
        float narrativeRate = buf.readFloat();
        int seniorStageIndex = buf.readInt();
        String personalityName = buf.readUtf();
        String personalityDesc = buf.readUtf();
        int poolCount = buf.readVarInt();
        String[] personalityPoolRefs = new String[poolCount];
        String[] personalityPoolNames = new String[poolCount];
        for (int i = 0; i < poolCount; i++) {
            personalityPoolRefs[i] = buf.readUtf();
            personalityPoolNames[i] = buf.readUtf();
        }
        return new VillagerLifeSyncPayload(
                entityId, birthYear, birthMonthIndex, birthDayOfMonth,
                birthMonthKey, birthMonthFallback, ageYears, stamped,
                isSenior, seniorProgressPermil,
                bioAgeDays, immortal, ageless, currentStageIndex,
                stageDays, keys, fallbacks, narrativeAge, stageScales, stageModelAges,
                stageNarrativeMin, stageNarrativeMax, narrativeRate, seniorStageIndex,
                personalityName, personalityDesc,
                personalityPoolRefs, personalityPoolNames
        );
    }
}
