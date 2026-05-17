package com.aetherianartificer.townstead.calendar;

public enum Season {
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER;

    public String translationKey() {
        return switch (this) {
            case SPRING -> "townstead.calendar.season.spring";
            case SUMMER -> "townstead.calendar.season.summer";
            case AUTUMN -> "townstead.calendar.season.autumn";
            case WINTER -> "townstead.calendar.season.winter";
        };
    }
}
