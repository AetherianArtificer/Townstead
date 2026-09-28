package com.aetherianartificer.townstead.politics.charter;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression checks against the real Minecraft buffer/component classes. */
class CharterWireTest {
    @Test
    void snapshotSurvivesWireRoundTrip() {
        var text = CharterSnapshotS2CPayload.Text.of(Component.translatableWithFallback(
                "charter.test.holders", "%s representatives: %s", 3,
                Component.translatableWithFallback("charter.test.name", "Mira %s", "Reed")).append(Component.literal(", ").append(Component.translatableWithFallback("test.event", "appointed"))));
        var action = new CharterSnapshotS2CPayload.Action("apply", literal("Apply"), text, false);
        var people = new ArrayList<CharterSnapshotS2CPayload.Person>();
        for (int i = 0; i < 100; i++) people.add(new CharterSnapshotS2CPayload.Person(
                "00000000-0000-0000-0000-" + String.format("%012d", i), literal("Citizen " + i), literal("Culture"), i == 0, i == 0));
        var office = new CharterSnapshotS2CPayload.Office("townstead:faction_leader", literal("Leader"), 1, -1,
                List.of(new CharterSnapshotS2CPayload.Holder("00000000-0000-0000-0000-000000000000", text, true)), true);
        var draft = new CharterSnapshotS2CPayload.Draft("token", List.of(new CharterSnapshotS2CPayload.Clause(text, literal("Now: Reedwater"))),
                List.of(new CharterSnapshotS2CPayload.Signer(literal("Mira"), literal("Leader"), literal("Autumn 3"),
                        new com.aetherianartificer.townstead.seal.PersonalSeal("townstead:key", 3), true, false)),
                false, true, 11000L);
        var book = new CharterSnapshotS2CPayload.Book("test:faction", text, literal("Amending as Leader"), literal("Accepted (68)"),
                literal("Needs: steady"), new CharterSnapshotS2CPayload.SeatRow(literal("Town Hall"), text, false, true),
                List.of(new CharterSnapshotS2CPayload.CensusScope("faction", literal("Faction population"),
                        List.of(new CharterSnapshotS2CPayload.CensusGroup("test:culture", literal("Culture"), 12, 0xff397f79)), 12, 3, true)),
                List.of(text), List.of(new CharterSnapshotS2CPayload.Heraldry("faction:test:one", text,
                        com.aetherianartificer.townstead.politics.heraldry.EmblemRecipe.DEFAULT.encode(), 7, true,
                        new CharterSnapshotS2CPayload.Livery("pack:plate", 0x8A1C1C, 0xC9A227, 2, literal("Culture default"), null))),
                true, List.of(office),
                new CharterSnapshotS2CPayload.Members(100, true, people, literal("You hold: Leader"), literal("Anyone may join."), List.of(action), 1),
                List.of(new CharterSnapshotS2CPayload.Request("request", text, literal("Applicant"), "application", "pending", 1, 2, List.of(action))),
                draft, new CharterSnapshotS2CPayload.Civic("test:provider", "test:actor", "active", true, false,
                        literal("Modded governance"), text, text, List.of(new CharterSnapshotS2CPayload.Role(text, List.of(text))),
                        List.of(text), List.of(action)),
                List.of(new CharterSnapshotS2CPayload.StyleOption("pack:plate", literal("Plate"),
                        new com.aetherianartificer.townstead.livery.LiveryView(net.minecraft.resources.ResourceLocation.tryParse("pack:plate"), true, 0x8A1C1C, 0xC9A227,
                                java.util.Map.of("chest", new com.aetherianartificer.townstead.livery.LiveryView.Trim("minecraft:ward", "minecraft:gold"))))),
                List.of(new CharterSnapshotS2CPayload.Welcome("vampire", literal("Vampires"), true)));
        var original = new CharterSnapshotS2CPayload(BlockPos.ZERO, new BlockPos(0, 1, 1), CharterSnapshotS2CPayload.FOUNDED, false,
                "", 42, "Settlement", "Faction", text, literal(""), literal("Founding tradition"),
                List.of(new CharterSnapshotS2CPayload.Option("test:profile", text, text, text, true, List.of("League of {name}"))),
                List.of(new CharterSnapshotS2CPayload.Option("test:culture", text, text, text, false, List.of("Thorncourt"))), book);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.write(buf);
            var decoded = CharterSnapshotS2CPayload.read(buf);
            assertTrue(original.equals(decoded), "Snapshot changed during wire round trip");
            assertTrue(buf.readableBytes() == 0, "Snapshot decoder left unread bytes");
            assertTrue(decoded.form().component().getString().equals("3 representatives: Mira Reed, appointed"),
                    "Nested translation arguments were lost");
            assertTrue(decoded.book().civic() != null && decoded.book().civic().controlsGovernment() && !decoded.book().civic().mayManage(),
                    "External governance authority changed");
            assertTrue(decoded.book().members().people().size() == 100, "Large roster was truncated");
            assertTrue(decoded.book().offices().get(0).maximum() == -1, "An unlimited office came back limited");
            assertTrue(decoded.book().census().get(0).uncounted() == 3, "Uncounted residents were lost");
            assertTrue(decoded.book().heraldry().get(0).livery().primary() == 0x8A1C1C, "A livery lost its colour");
            assertTrue(decoded.book().members().livery() == 1 && decoded.book().liveryStyles().size() == 1, "Livery state was lost");
            assertTrue(decoded.book().liveryStyles().get(0).view().trims().get("chest").pattern().equals("minecraft:ward"), "A style lost its trims");
            assertTrue(decoded.book().draft().signers().get(0).date().fallback().equals("Autumn 3"), "A seal lost its date");
            assertTrue(decoded.book().draft().signers().get(0).seal().device().equals("townstead:key"), "A seal lost its device");
        } finally { buf.release(); }
    }

    @Test
    void anUnfoundedSnapshotHasNoBook() {
        var original = new CharterSnapshotS2CPayload(BlockPos.ZERO, BlockPos.ZERO, CharterSnapshotS2CPayload.UNFOUNDED, true,
                "", 0, "", "", literal("Not founded"), literal(""), literal("No founding culture"), List.of(), List.of(), null);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.write(buf);
            var decoded = CharterSnapshotS2CPayload.read(buf);
            assertTrue(original.equals(decoded) && decoded.book() == null, "An unfounded snapshot changed during wire round trip");
        } finally { buf.release(); }
    }

    @Test
    void actionIntentSurvivesWireRoundTrip() {
        var intent = new CharterActionC2SPayload(BlockPos.ZERO, CharterActionC2SPayload.DRAFT,
                "", "", "", "remove", "draft-token", "2", 42);
        FriendlyByteBuf actionBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            intent.write(actionBuffer);
            assertTrue(intent.equals(CharterActionC2SPayload.read(actionBuffer)), "Action intent lost target or revision");
            assertTrue(actionBuffer.readableBytes() == 0, "Action decoder left bytes unread");
        } finally { actionBuffer.release(); }
    }

    @Test
    void malformedListCountsAreRejected() {
        for (int invalid : new int[]{-1, 4097}) {
            FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
            try {
                bad.writeUtf("test:key"); bad.writeUtf(""); bad.writeVarInt(invalid);
                assertThrows(IllegalArgumentException.class, () -> CharterSnapshotS2CPayload.Text.read(bad),
                        "Invalid list count accepted: " + invalid);
            } finally { bad.release(); }
        }
    }

    @Test
    void unboundedTranslationNestingIsRejected() {
        Component nested = Component.literal("leaf");
        for (int i = 0; i < 18; i++) nested = Component.translatableWithFallback("test:nested", "%s", nested);
        Component deep = nested;
        assertThrows(IllegalArgumentException.class, () -> CharterSnapshotS2CPayload.Text.of(deep),
                "Unbounded translation nesting accepted");
    }

    private static CharterSnapshotS2CPayload.Text literal(String value) { return new CharterSnapshotS2CPayload.Text("", value); }
}
