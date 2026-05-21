package com.example.markdownviewer;

import android.graphics.Color;
import android.view.View;
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
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int left = v.getPaddingLeft();
            int right = v.getPaddingRight();
            int padTop = top ? statusBarHeight : v.getPaddingTop();
            int padBottom = bottom ? navBarHeight : v.getPaddingBottom();
            v.setPadding(left, padTop, right, padBottom);
            return insets;
        });
    }
}
