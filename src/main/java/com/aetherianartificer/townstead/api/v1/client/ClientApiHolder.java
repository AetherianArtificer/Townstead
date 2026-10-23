package com.aetherianartificer.townstead.api.v1.client;

final class ClientApiHolder {
    private static volatile TownsteadClientApiV1 instance;

    private ClientApiHolder() {}

    static TownsteadClientApiV1 get() {
        TownsteadClientApiV1 api = instance;
        if (api == null) {
            synchronized (ClientApiHolder.class) {
                api = instance;
                if (api == null) {
                    try {
                        api = (TownsteadClientApiV1) Class.forName(TownsteadClientApiV1.IMPLEMENTATION)
                                .getDeclaredConstructor().newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Townstead client API implementation is missing", e);
                    }
                    instance = api;
                }
            }
        }
        return api;
    }
}
