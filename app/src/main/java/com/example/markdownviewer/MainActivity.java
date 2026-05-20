package com.example.markdownviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public class MainActivity extends AppCompatActivity {

    private LinearLayout recentContainer;
    private BlurView blurView;
    private FrameLayout backgroundContainer;

    private final ActivityResultLauncher<Intent> openFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        RecentFilesManager.addRecentFile(this, uri);
                        Intent intent = new Intent(this, MarkdownActivity.class);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SystemBarUtils.applyLightSystemBars(getWindow());

        blurView = findViewById(R.id.blur_view);
        backgroundContainer = findViewById(R.id.background_container);
        BlurHelper.setup(this, blurView);

        recentContainer = findViewById(R.id.recent_files_container);

        findViewById(R.id.btn_open_file).setOnClickListener(v -> openFilePicker());
        findViewById(R.id.btn_browse).setOnClickListener(v ->
                startActivity(new Intent(this, FilePickerActivity.class)));
        findViewById(R.id.btn_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        refreshRecentFiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecentFiles();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"text/markdown", "text/x-markdown", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        openFileLauncher.launch(intent);
    }

    private void refreshRecentFiles() {
        if (recentContainer == null) return;
        recentContainer.removeAllViews();
        List<RecentFilesManager.RecentEntry> list = RecentFilesManager.getRecentFiles(this);
        if (list.isEmpty()) {
            recentContainer.setVisibility(View.GONE);
            findViewById(R.id.tv_recent_label).setVisibility(View.GONE);
            return;
        }
        recentContainer.setVisibility(View.VISIBLE);
        findViewById(R.id.tv_recent_label).setVisibility(View.VISIBLE);
        for (RecentFilesManager.RecentEntry entry : list) {
            View item = LayoutInflater.from(this).inflate(R.layout.item_recent_file, recentContainer, false);
            TextView tvName = item.findViewById(R.id.tv_file_name);
            String displayName = entry.name;
            if (displayName == null || displayName.isEmpty()) {
                displayName = FileUtils.getDisplayName(this, Uri.parse(entry.uri));
            }
            tvName.setText(displayName);
            item.setOnClickListener(v -> {
                try {
                    Uri uri = Uri.parse(entry.uri);
                    if ("content".equals(uri.getScheme())) {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    Intent intent = new Intent(this, MarkdownActivity.class);
                    intent.setData(uri);
                    startActivity(intent);
                } catch (SecurityException | IllegalArgumentException e) {
                    android.widget.Toast.makeText(this, "该文件已移位或失效，已自动清除记录", android.widget.Toast.LENGTH_SHORT).show();
                    RecentFilesManager.removeRecentFile(this, entry.uri);
                    refreshRecentFiles();
                }
            });
            recentContainer.addView(item);
        }
    }
}
