package com.aetherianartificer.townstead.pheno.value;

import com.google.gson.JsonObject;

/** Pluggable value-source contract (the object form of a number). Register via {@link ValueTypes#register}. */
public interface ValueType {

    String key();

    Value parse(JsonObject json);
}
