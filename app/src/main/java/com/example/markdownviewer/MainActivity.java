package com.example.markdownviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eightbitlab.com.blurview.BlurView;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerRecentFiles;
    private RecentFileAdapter recentFileAdapter;
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
        SystemBarUtils.applyInsetsToView(findViewById(R.id.btn_about), true, false);

        blurView = findViewById(R.id.blur_view);
        backgroundContainer = findViewById(R.id.background_container);
        BlurHelper.setup(this, blurView);

        recyclerRecentFiles = findViewById(R.id.recycler_recent_files);
        recyclerRecentFiles.setLayoutManager(new LinearLayoutManager(this));
        recentFileAdapter = new RecentFileAdapter(this::onRecentFileClick);
        recyclerRecentFiles.setAdapter(recentFileAdapter);

        findViewById(R.id.btn_open_file).setOnClickListener(v -> openFilePicker());
        findViewById(R.id.btn_browse).setOnClickListener(v ->
                startActivity(new Intent(this, FilePickerActivity.class)));
        findViewById(R.id.btn_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        View btnClear = findViewById(R.id.btn_clear_recent);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                RecentFilesManager.clear(this);
                refreshRecentFiles();
            });
        }

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
        List<RecentFilesManager.RecentEntry> list = RecentFilesManager.getRecentFiles(this);
        View layoutHeader = findViewById(R.id.layout_recent_header);
        if (list.isEmpty()) {
            recyclerRecentFiles.setVisibility(View.GONE);
            if (layoutHeader != null) layoutHeader.setVisibility(View.GONE);
            return;
        }
        recyclerRecentFiles.setVisibility(View.VISIBLE);
        if (layoutHeader != null) layoutHeader.setVisibility(View.VISIBLE);

        int count = Math.min(list.size(), Constants.MAX_RECENT_DISPLAY);
        recentFileAdapter.submitList(list.subList(0, count));
    }

    private void onRecentFileClick(RecentFilesManager.RecentEntry entry) {
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
            android.widget.Toast.makeText(this, R.string.recent_file_invalid, android.widget.Toast.LENGTH_SHORT).show();
            RecentFilesManager.removeRecentFile(this, entry.uri);
            refreshRecentFiles();
        }
    }
}
