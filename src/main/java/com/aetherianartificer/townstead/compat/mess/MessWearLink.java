package com.aetherianartificer.townstead.compat.mess;

import com.aetherianartificer.bitofamess.api.v1.BodiesApi;
import com.aetherianartificer.bitofamess.api.v1.MessApiV1;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/** Bit of a Mess's bathing wear, through its API (revision 4). Kept apart so an older Bit of a Mess never loads it. */
final class MessWearLink {

    private MessWearLink() {}

    private static BodiesApi bodies() {
        MessApiV1 api = MessApiV1.get();
        return api.getApiRevision() >= 4 ? api.bodies() : null;
    }

    static Optional<String> wearOf(LivingEntity body) {
        BodiesApi bodies = bodies();
        return bodies == null ? Optional.empty() : bodies.bathingWearOf(body);
    }

    static Optional<String> pick(String gender, long seed) {
        BodiesApi bodies = bodies();
        return bodies == null ? Optional.empty() : bodies.pickBathingWear(gender, seed);
    }

    static void set(LivingEntity body, String wear, int color) {
        BodiesApi bodies = bodies();
        if (bodies != null) bodies.setBathingWear(body, "townstead", wear, color, false);
    }
}
