package com.example.markdownviewer;

import android.content.Context;
import android.os.Build;
import android.view.ViewGroup;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public final class BlurHelper {

    /** 模糊半径，单位 dp */
    private static final float BLUR_RADIUS = 20f;

    /** 浅色主题下模糊叠加颜色：白色 40% 不透明度 (ARGB: 0x66FFFFFF) */
    private static final int OVERLAY_COLOR_LIGHT = 0x66FFFFFF;
    /** 深色主题下模糊叠加颜色：黑色 40% 不透明度 (ARGB: 0x66000000) */
    private static final int OVERLAY_COLOR_DARK = 0x66000000;

    public static void setup(Context context, BlurView blurView) {
        if (blurView == null) return;
        if (!(blurView.getParent() instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) blurView.getParent();

        boolean dark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int overlayColor = dark ? OVERLAY_COLOR_DARK : OVERLAY_COLOR_LIGHT;

        // 低端设备降级：低内存设备跳过实时模糊以避免性能问题
        if (shouldSkipBlur(context)) {
            blurView.setOverlayColor(overlayColor);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurView.setupWith(root, new RenderEffectBlur())
                    .setBlurRadius(BLUR_RADIUS)
                    .setOverlayColor(overlayColor);
        } else {
            blurView.setupWith(root, new RenderScriptBlur(context))
                    .setBlurRadius(BLUR_RADIUS)
                    .setOverlayColor(overlayColor);
        }
    }

    private static boolean shouldSkipBlur(Context context) {
        // 低内存设备直接跳过实时模糊以避免性能问题
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return am != null && am.isLowRamDevice();
        }
        return false;
    }

    private BlurHelper() {}
}
