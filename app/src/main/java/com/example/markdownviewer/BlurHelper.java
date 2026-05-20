package com.example.markdownviewer;

import android.content.Context;
import android.os.Build;
import android.view.ViewGroup;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public final class BlurHelper {

    private static final float BLUR_RADIUS = 20f;
    private static final int OVERLAY_COLOR = 0x66FFFFFF;

    public static void setup(Context context, BlurView blurView) {
        ViewGroup root = (ViewGroup) blurView.getParent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurView.setupWith(root, new RenderEffectBlur())
                    .setBlurRadius(BLUR_RADIUS)
                    .setOverlayColor(OVERLAY_COLOR);
        } else {
            blurView.setupWith(root, new RenderScriptBlur(context))
                    .setBlurRadius(BLUR_RADIUS)
                    .setOverlayColor(OVERLAY_COLOR);
        }
    }

    private BlurHelper() {}
}
