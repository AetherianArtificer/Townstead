package com.aetherianartificer.townstead.politics.charter;

/**
 * Small state bridge mixed into vanilla lecterns. The value drives only the virtual civic
 * presentation; no charter item is inserted into the lectern inventory.
 */
public interface CharterLecternAccess {
    int NONE = 0;
    int PREPARED = 1;
    int FOUNDED = 2;

    String townstead$emblem();
    void townstead$setEmblem(String recipe);

    int townstead$charterState();

    void townstead$setCharterState(int state);
}
