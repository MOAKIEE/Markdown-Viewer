package com.example.markdownviewer;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

public final class ReaderTheme {

    public static final int MODE_CLASSIC = 0;
    public static final int MODE_SEPIA = 1;
    public static final int MODE_GREEN = 2;
    public static final int MODE_SPACE = 3;

    private ReaderTheme() {}

    public static boolean isDarkMode(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    @ColorInt
    public static int getBgColor(Context context, int mode) {
        return color(context, resId(context, mode,
                R.color.theme_sepia_bg, R.color.theme_green_bg, R.color.theme_space_bg),
                R.color.ios_background);
    }

    @ColorInt
    public static int getCardColor(Context context, int mode) {
        return color(context, resId(context, mode,
                R.color.theme_sepia_card, R.color.theme_green_card, R.color.theme_space_card),
                R.color.ios_card_bg);
    }

    @ColorInt
    public static int getTextColor(Context context, int mode) {
        return color(context, resId(context, mode,
                R.color.theme_sepia_text, R.color.theme_green_text, R.color.theme_space_text),
                R.color.ios_text_primary);
    }

    @ColorInt
    public static int getToolbarColor(Context context, int mode) {
        boolean dark = isDarkMode(context);
        switch (mode) {
            case MODE_SEPIA: return ContextCompat.getColor(context, R.color.theme_sepia_toolbar);
            case MODE_GREEN: return ContextCompat.getColor(context, R.color.theme_green_toolbar);
            case MODE_SPACE: return ContextCompat.getColor(context, R.color.theme_space_toolbar);
            default: return ContextCompat.getColor(context,
                    dark ? R.color.theme_classic_toolbar_dark : R.color.theme_classic_toolbar_light);
        }
    }

    @ColorInt
    public static int getHintColor(Context context, int mode) {
        return color(context, resId(context, mode,
                R.color.theme_sepia_hint, R.color.theme_green_hint, R.color.theme_space_hint),
                R.color.ios_text_secondary);
    }

    @ColorInt
    public static int getCodeBg(Context context, int mode) {
        return color(context, resId(context, mode,
                R.color.theme_sepia_code_bg, R.color.theme_green_code_bg, R.color.theme_space_code_bg),
                R.color.theme_classic_code_bg);
    }

    @ColorInt
    public static int getCodeBlockBg(Context context, int mode) {
        return color(context, resId(context, mode,
                R.color.theme_sepia_code_block_bg, R.color.theme_green_code_block_bg, R.color.theme_space_code_block_bg),
                R.color.theme_classic_code_block_bg);
    }

    @ColorInt
    public static int getBlockQuoteColor(Context context, int mode) {
        return color(context, resId(context, mode,
                R.color.theme_sepia_block_quote, R.color.theme_green_block_quote, R.color.theme_space_block_quote),
                R.color.theme_classic_block_quote);
    }

    @ColorInt
    public static int getLinkColor(Context context, int mode) {
        boolean dark = isDarkMode(context);
        switch (mode) {
            case MODE_SEPIA: return ContextCompat.getColor(context, R.color.theme_sepia_link);
            case MODE_GREEN: return ContextCompat.getColor(context, R.color.theme_green_link);
            case MODE_SPACE: return ContextCompat.getColor(context, R.color.theme_space_link);
            default: return ContextCompat.getColor(context,
                    dark ? R.color.theme_classic_link_dark : R.color.theme_classic_link_light);
        }
    }

    @ColorInt
    public static int getHighlightColor(Context context, int mode) {
        boolean dark = isDarkMode(context);
        if (dark || mode == MODE_GREEN || mode == MODE_SPACE) {
            return ContextCompat.getColor(context, R.color.search_highlight_dark);
        }
        return ContextCompat.getColor(context, R.color.search_highlight_light);
    }

    @ColorInt
    public static int getCurrentHighlightColor(Context context, int mode) {
        boolean dark = isDarkMode(context);
        if (dark || mode == MODE_GREEN || mode == MODE_SPACE) {
            return ContextCompat.getColor(context, R.color.search_highlight_current_dark);
        }
        return ContextCompat.getColor(context, R.color.search_highlight_current_light);
    }

    public static boolean useLightStatusBar(int mode) {
        return mode == MODE_CLASSIC || mode == MODE_SEPIA;
    }

    @ColorRes
    private static int resId(Context context, int mode, @ColorRes int sepia, @ColorRes int green, @ColorRes int space) {
        switch (mode) {
            case MODE_SEPIA: return sepia;
            case MODE_GREEN: return green;
            case MODE_SPACE: return space;
            default: return 0;
        }
    }

    @ColorInt
    private static int color(Context context, @ColorRes int specific, @ColorRes int fallback) {
        return specific != 0 ? ContextCompat.getColor(context, specific) : ContextCompat.getColor(context, fallback);
    }
}
