package com.example.markdownviewer;

public final class Constants {
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    public static final int MAX_RECENT = 5;
    public static final int MAX_RECENT_DISPLAY = 5;

    // Reader config
    public static final int FONT_SIZE_DEFAULT = 16;
    public static final int FONT_SIZE_MIN = 12;
    public static final int FONT_SIZE_MAX = 32;
    public static final float LINE_SPACING_DEFAULT = 1.3f;
    public static final String PREFS_READER_CONFIG = "reader_config";

    // Animation
    public static final int ANIM_DURATION_FADE = 200;
    public static final int ANIM_DURATION_APPEAR = 250;

    // Search debounce
    public static final int SEARCH_DEBOUNCE_MS = 300;

    private Constants() {}
}
