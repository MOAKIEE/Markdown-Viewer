package com.example.markdownviewer;

import android.graphics.Color;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SystemBarUtils {

    public static void applyLightSystemBars(Window window) {
        setSystemBarAppearance(window, true);
    }

    public static void applyDarkSystemBars(Window window) {
        setSystemBarAppearance(window, false);
    }

    public static void applySystemBarsForCurrentTheme(Window window, Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        setSystemBarAppearance(window, shouldUseLightSystemBars(nightMode));
    }

    private static void setSystemBarAppearance(Window window, boolean lightBars) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(lightBars);
        controller.setAppearanceLightNavigationBars(lightBars);
    }

    public static void applyInsetsToView(View view, boolean top, boolean bottom) {
        if (view == null) return;
        Padding originalPadding = new Padding(
                view.getPaddingLeft(),
                view.getPaddingTop(),
                view.getPaddingRight(),
                view.getPaddingBottom());
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            Padding padding = resolveInsetsPadding(originalPadding, statusBarHeight, navBarHeight, top, bottom);
            v.setPadding(padding.left, padding.top, padding.right, padding.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    public static void applyInsetsToMargins(View view, boolean top, boolean bottom) {
        if (view == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
        EdgeInsets originalMargins = new EdgeInsets(
                marginParams.leftMargin,
                marginParams.topMargin,
                marginParams.rightMargin,
                marginParams.bottomMargin);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            EdgeInsets margins = resolveInsetsMargins(originalMargins, statusBarHeight, navBarHeight, top, bottom);
            ViewGroup.LayoutParams currentParams = v.getLayoutParams();
            if (currentParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams currentMargins = (ViewGroup.MarginLayoutParams) currentParams;
                currentMargins.leftMargin = margins.left;
                currentMargins.topMargin = margins.top;
                currentMargins.rightMargin = margins.right;
                currentMargins.bottomMargin = margins.bottom;
                v.setLayoutParams(currentMargins);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    static Padding resolveInsetsPadding(
            Padding originalPadding,
            int statusBarHeight,
            int navBarHeight,
            boolean top,
            boolean bottom) {
        return new Padding(
                originalPadding.left,
                originalPadding.top + (top ? statusBarHeight : 0),
                originalPadding.right,
                originalPadding.bottom + (bottom ? navBarHeight : 0));
    }

    static EdgeInsets resolveInsetsMargins(
            EdgeInsets originalMargins,
            int statusBarHeight,
            int navBarHeight,
            boolean top,
            boolean bottom) {
        return new EdgeInsets(
                originalMargins.left,
                originalMargins.top + (top ? statusBarHeight : 0),
                originalMargins.right,
                originalMargins.bottom + (bottom ? navBarHeight : 0));
    }

    static boolean shouldUseLightSystemBars(int nightMode) {
        return nightMode != Configuration.UI_MODE_NIGHT_YES;
    }

    static final class Padding {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Padding(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    static final class EdgeInsets {
        final int left;
        final int top;
        final int right;
        final int bottom;

        EdgeInsets(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
