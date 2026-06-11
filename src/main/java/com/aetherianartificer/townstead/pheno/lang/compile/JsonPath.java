package com.aetherianartificer.townstead.pheno.lang.compile;

/**
 * An immutable JSON-pointer-ish path used to locate a {@link Diagnostic} inside a resource,
 * rendered in the familiar {@code $.action.do[0].duration} form. Each {@link #field} or
 * {@link #index} returns a new path so a recursive walker can thread location without mutable
 * state.
 */
public final class JsonPath {

    public static final JsonPath ROOT = new JsonPath("$");

    private final String rendered;

    private JsonPath(String rendered) {
        this.rendered = rendered;
    }

    public JsonPath field(String name) {
        return new JsonPath(rendered + "." + name);
    }

    public JsonPath index(int i) {
        return new JsonPath(rendered + "[" + i + "]");
    }

    @Override
    public String toString() {
        return rendered;
    }
}
