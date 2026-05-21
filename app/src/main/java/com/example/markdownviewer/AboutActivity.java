package com.example.markdownviewer;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import eightbitlab.com.blurview.BlurView;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        SystemBarUtils.applyLightSystemBars(getWindow());
        SystemBarUtils.applyInsetsToView(findViewById(R.id.top_bar), true, false);

        BlurView blurView = findViewById(R.id.blur_view);
        BlurHelper.setup(this, blurView);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        TextView tvVersion = findViewById(R.id.tv_version);
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(getString(R.string.about_version, versionName));
        } catch (Exception e) {
            tvVersion.setText(getString(R.string.about_version, "?"));
        }
    }
}
