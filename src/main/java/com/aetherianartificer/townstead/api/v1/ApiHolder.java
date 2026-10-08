package com.aetherianartificer.townstead.api.v1;

/** Lazily resolves the implementation named by {@link TownsteadApiV1#IMPLEMENTATION}. */
final class ApiHolder {
    private static volatile TownsteadApiV1 instance;

    private ApiHolder() {}

    static TownsteadApiV1 get() {
        TownsteadApiV1 api = instance;
        if (api == null) {
            synchronized (ApiHolder.class) {
                api = instance;
                if (api == null) {
                    try {
                        api = (TownsteadApiV1) Class.forName(TownsteadApiV1.IMPLEMENTATION)
                                .getDeclaredConstructor().newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Townstead API implementation is missing", e);
                    }
                    instance = api;
                }
            }
        }
        return api;
    }
}
