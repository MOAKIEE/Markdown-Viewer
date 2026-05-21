package com.example.markdownviewer;

import android.content.Context;
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

        int overlayColor;
        boolean dark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        overlayColor = dark ? 0x66000000 : OVERLAY_COLOR_LIGHT;

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

    private BlurHelper() {}
}
