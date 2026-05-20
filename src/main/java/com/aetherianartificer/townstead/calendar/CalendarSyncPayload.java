package com.aetherianartificer.townstead.calendar;

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
 * Server-to-client snapshot of today's calendar state plus the active
 * profile's shape so clients can render arbitrary months/years in the
 * calendar UI without further round-trips.
 *
 * Broadcast on player login, on every day rollover
 * ({@link WorldCalendarTicker}), and on profile/epoch changes
 * ({@link TownsteadCalendar#setProfileOverride} etc.).
 *
 * Text fields are carried as (translate key, fallback) pairs rather than
 * pre-resolved strings so each client resolves to its own locale. Months are
 * a parallel-array bundle: {@code monthKeys[i]}, {@code monthFallbacks[i]},
 * {@code monthDays[i]} describe month index {@code i}.
 *
 * Per-profile format overrides are <em>not</em> carried in this payload — the
 * client-side calendar UI uses the global
 * {@code townstead.calendar.format.<style>} keys so locale handling stays
 * client-driven. Profile-specific format overrides only affect dates rendered
 * server-side (commands, tooltips) where the active profile is directly
 * available.
 */
public record CalendarSyncPayload(
        long worldDay,
        int year,
        int monthIndex,
        int dayOfMonth,
        int dayOfYear,
        int dayOfWeek,
        String monthKey,
        String monthFallback,
        String profileKey,
        String profileFallback,
        String seasonKey,
        // Profile shape (for the calendar UI grid)
        int daysPerWeek,
        int epochYearOffset,
        String yearSuffixKey,
        String yearSuffixFallback,
        List<String> monthKeys,
        List<String> monthFallbacks,
        List<Integer> monthDays,
        // Optional weekday names. Empty lists = no weekdays defined; UI uses
        // numeric headers. When present, length == daysPerWeek.
        List<String> weekdayLongKeys,
        List<String> weekdayLongFallbacks,
        List<String> weekdayShortKeys,
        List<String> weekdayShortFallbacks,
        // Optional eras. When non-empty, the formatter resolves the displayed
        // year and era name from this list and ignores yearSuffix*.
        // direction: 0 = ascending, 1 = descending.
        List<String> eraNameKeys,
        List<String> eraNameFallbacks,
        List<Integer> eraStartYears,
        List<Integer> eraFirstYearDisplayedAs,
        List<Integer> eraDirections,
        // Optional leap rules. Empty list = profile has no leap rules and
        // every year has the same month layout. Wire format handled by
        // LeapRuleCodec so this payload doesn't bake the rule schema in.
        List<LeapRule> leapRules
//? if neoforge {
) implements CustomPacketPayload {
//?} else {
/*) {
*///?}

    //? if neoforge {
    public static final Type<CalendarSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "calendar_sync"));

    public static final StreamCodec<FriendlyByteBuf, CalendarSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> p.write(buf),
            CalendarSyncPayload::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "calendar_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "calendar_sync");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(worldDay);
        buf.writeVarInt(year);
        buf.writeVarInt(monthIndex);
        buf.writeVarInt(dayOfMonth);
        buf.writeVarInt(dayOfYear);
        buf.writeVarInt(dayOfWeek);
        buf.writeUtf(monthKey);
        buf.writeUtf(monthFallback);
        buf.writeUtf(profileKey);
        buf.writeUtf(profileFallback);
        buf.writeUtf(seasonKey);
        buf.writeVarInt(daysPerWeek);
        buf.writeVarInt(epochYearOffset);
        buf.writeUtf(yearSuffixKey);
        buf.writeUtf(yearSuffixFallback);
        int n = monthKeys.size();
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(monthKeys.get(i));
            buf.writeUtf(monthFallbacks.get(i));
            buf.writeVarInt(monthDays.get(i));
        }
        int w = weekdayLongKeys.size();
        buf.writeVarInt(w);
        for (int i = 0; i < w; i++) {
            buf.writeUtf(weekdayLongKeys.get(i));
            buf.writeUtf(weekdayLongFallbacks.get(i));
            buf.writeUtf(weekdayShortKeys.get(i));
            buf.writeUtf(weekdayShortFallbacks.get(i));
        }
        int e = eraNameKeys.size();
        buf.writeVarInt(e);
        for (int i = 0; i < e; i++) {
            buf.writeUtf(eraNameKeys.get(i));
            buf.writeUtf(eraNameFallbacks.get(i));
            buf.writeVarInt(eraStartYears.get(i));
            buf.writeVarInt(eraFirstYearDisplayedAs.get(i));
            buf.writeVarInt(eraDirections.get(i));
        }
        LeapRuleCodec.writeList(buf, leapRules);
    }

    public static CalendarSyncPayload read(FriendlyByteBuf buf) {
        long worldDay = buf.readLong();
        int year = buf.readVarInt();
        int monthIndex = buf.readVarInt();
        int dayOfMonth = buf.readVarInt();
        int dayOfYear = buf.readVarInt();
        int dayOfWeek = buf.readVarInt();
        String monthKey = buf.readUtf();
        String monthFallback = buf.readUtf();
        String profileKey = buf.readUtf();
        String profileFallback = buf.readUtf();
        String seasonKey = buf.readUtf();
        int daysPerWeek = buf.readVarInt();
        int epochYearOffset = buf.readVarInt();
        String yearSuffixKey = buf.readUtf();
        String yearSuffixFallback = buf.readUtf();
        int n = buf.readVarInt();
        List<String> monthKeys = new ArrayList<>(n);
        List<String> monthFallbacks = new ArrayList<>(n);
        List<Integer> monthDays = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            monthKeys.add(buf.readUtf());
            monthFallbacks.add(buf.readUtf());
            monthDays.add(buf.readVarInt());
        }
        int w = buf.readVarInt();
        List<String> wdLongKeys = new ArrayList<>(w);
        List<String> wdLongFallbacks = new ArrayList<>(w);
        List<String> wdShortKeys = new ArrayList<>(w);
        List<String> wdShortFallbacks = new ArrayList<>(w);
        for (int i = 0; i < w; i++) {
            wdLongKeys.add(buf.readUtf());
            wdLongFallbacks.add(buf.readUtf());
            wdShortKeys.add(buf.readUtf());
            wdShortFallbacks.add(buf.readUtf());
        }
        int e = buf.readVarInt();
        List<String> eraNameKeys = new ArrayList<>(e);
        List<String> eraNameFallbacks = new ArrayList<>(e);
        List<Integer> eraStartYears = new ArrayList<>(e);
        List<Integer> eraFirstYearDisplayedAs = new ArrayList<>(e);
        List<Integer> eraDirections = new ArrayList<>(e);
        for (int i = 0; i < e; i++) {
            eraNameKeys.add(buf.readUtf());
            eraNameFallbacks.add(buf.readUtf());
            eraStartYears.add(buf.readVarInt());
            eraFirstYearDisplayedAs.add(buf.readVarInt());
            eraDirections.add(buf.readVarInt());
        }
        List<LeapRule> leapRules = LeapRuleCodec.readList(buf);
        return new CalendarSyncPayload(
                worldDay, year, monthIndex, dayOfMonth, dayOfYear, dayOfWeek,
                monthKey, monthFallback, profileKey, profileFallback, seasonKey,
                daysPerWeek, epochYearOffset, yearSuffixKey, yearSuffixFallback,
                monthKeys, monthFallbacks, monthDays,
                wdLongKeys, wdLongFallbacks, wdShortKeys, wdShortFallbacks,
                eraNameKeys, eraNameFallbacks, eraStartYears, eraFirstYearDisplayedAs, eraDirections,
                leapRules
        );
    }
}
