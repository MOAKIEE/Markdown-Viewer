package com.example.markdownviewer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.view.ViewGroup;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public final class BlurHelper {

    private static final float BLUR_RADIUS = 20f;
    private static final int OVERLAY_COLOR_LIGHT = 0x66FFFFFF;

    public static void setup(Context context, BlurView blurView) {
        if (blurView == null) return;
        if (!(blurView.getParent() instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) blurView.getParent();

        boolean dark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int overlayColor = dark ? 0x66000000 : OVERLAY_COLOR_LIGHT;

        // 低端设备降级：DEBUG 构建或 Android Go / 低内存设备跳过实时模糊
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
        // DEBUG 构建时可通过开发者选项开关关闭模糊，方便性能测试
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            // 可在此读取 SharedPreferences 做开关，默认保持开启
            return false;
        }
        // Android Go 或低内存设备直接跳过
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return context.getPackageManager().isInstantApp()
                    || (am != null && am.isLowRamDevice());
        }
        return false;
    }

    private BlurHelper() {}
}
