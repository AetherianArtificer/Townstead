package com.aetherianartificer.townstead.pheno.action.types;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyedSocialActionTypesTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void moodNeedsAKeyAndAnAmountInRange() {
        var type = new KeyedSocialActionTypes.AdjustMood();
        assertNotNull(type.parse(json("{\"key\":\"my_pack:clean_home\",\"amount\":2,\"duration_ticks\":24000}")));
        assertNotNull(type.parse(json("{\"key\":\"my_pack:dirty_home\",\"amount\":-15}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("{\"amount\":2}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("{\"key\":\"clean_home\",\"amount\":2}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("{\"key\":\"my_pack:a\",\"amount\":0}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("{\"key\":\"my_pack:a\",\"amount\":16}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("{\"key\":\"my_pack:a\",\"amount\":1,\"duration_ticks\":-1}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("{\"key\":\"my_pack:a\",\"amount\":1,\"for\":5}")));
    }

    @Test
    void heartsTakeACapThatIsNotNegative() {
        var type = new KeyedSocialActionTypes.AdjustHearts();
        assertNotNull(type.parse(json("{\"key\":\"my_pack:room_thanks\",\"amount\":2,\"daily_cap\":2}")));
        assertNotNull(type.parse(json("{\"key\":\"my_pack:splash\",\"amount\":-2}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("{\"key\":\"my_pack:a\",\"amount\":2,\"daily_cap\":-1}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("{\"key\":\"my_pack:a\",\"amount\":101}")));
    }

    @Test
    void clearingAndReturningNeedOnlyAKey() {
        assertNotNull(new KeyedSocialActionTypes.ClearMood().parse(json("{\"key\":\"my_pack:clean_home\"}")));
        assertNotNull(new KeyedSocialActionTypes.ReturnHearts().parse(json("{\"key\":\"my_pack:dumped\"}")));
        assertThrows(IllegalArgumentException.class, () -> new KeyedSocialActionTypes.ReturnHearts().parse(json("{}")));
    }

    @Test
    void aSocialOutcomeDailyLimitNeedsAKey() {
        var type = new SocialOutcomeActionType();
        String change = "\"actor_relationship\":[{\"quality\":\"townstead:respect\",\"amount\":1,\"half_life_days\":7}]";
        assertNotNull(type.parse(json("{\"source\":\"my_pack:cleaning\"," + change + ",\"key\":\"my_pack:cleaning\",\"daily_limit\":1}")));
        assertNotNull(type.parse(json("{\"source\":\"my_pack:cleaning\"," + change + "}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("{\"source\":\"my_pack:cleaning\"," + change + ",\"daily_limit\":1}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("{\"source\":\"my_pack:cleaning\"," + change + ",\"key\":\"my_pack:c\",\"daily_limit\":-1}")));
    }
}
