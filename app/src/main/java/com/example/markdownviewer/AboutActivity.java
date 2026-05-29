package com.example.markdownviewer;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.markdownviewer.databinding.ActivityAboutBinding;

public class AboutActivity extends AppCompatActivity {

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SystemBarUtils.applySystemBarsForCurrentTheme(getWindow(), this);
        SystemBarUtils.applyInsetsToView(binding.topBar, true, false);

        BlurHelper.setup(this, binding.blurView);

        binding.btnBack.setOnClickListener(v -> finish());

        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            binding.tvVersion.setText(getString(R.string.about_version, versionName));
        } catch (PackageManager.NameNotFoundException e) {
            Log.w("AboutActivity", "Failed to get package info", e);
            binding.tvVersion.setText(getString(R.string.about_version, "?"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
