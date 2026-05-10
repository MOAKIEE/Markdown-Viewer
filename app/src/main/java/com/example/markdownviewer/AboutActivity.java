package com.example.markdownviewer;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public class AboutActivity extends AppCompatActivity {

    private BlurView blurView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        SystemBarUtils.applyLightSystemBars(getWindow());

        blurView = findViewById(R.id.blur_view);
        setupBlur();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        TextView tvVersion = findViewById(R.id.tv_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            tvVersion.setText("版本: " + version);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void setupBlur() {
        ViewGroup root = (ViewGroup) blurView.getParent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurView.setupWith(root, new RenderEffectBlur())
                    .setBlurRadius(20f)
                    .setOverlayColor(0x66FFFFFF);
        } else {
            blurView.setupWith(root, new RenderScriptBlur(this))
                    .setBlurRadius(20f)
                    .setOverlayColor(0x66FFFFFF);
        }
    }
}