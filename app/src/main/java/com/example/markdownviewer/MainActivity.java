package com.example.markdownviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.markdownviewer.databinding.ActivityMainBinding;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private RecentFileAdapter recentFileAdapter;

    private final ActivityResultLauncher<Intent> openFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        boolean hasPermission = UriPermissionUtils.takeReadPermission(getContentResolver(), uri);
                        if (!hasPermission) {
                            android.widget.Toast.makeText(this, R.string.error_no_permission, android.widget.Toast.LENGTH_SHORT).show();
                        }
                        RecentFilesManager.addRecentFile(this, uri);
                        Intent intent = new Intent(this, MarkdownActivity.class);
                        intent.setData(uri);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SystemBarUtils.applySystemBarsForCurrentTheme(getWindow(), this);
        SystemBarUtils.applyInsetsToMargins(binding.btnAbout, true, false);

        BlurHelper.setup(this, binding.blurView);

        binding.recyclerRecentFiles.setLayoutManager(new LinearLayoutManager(this));
        recentFileAdapter = new RecentFileAdapter(this::onRecentFileClick);
        binding.recyclerRecentFiles.setAdapter(recentFileAdapter);

        binding.btnOpenFile.setOnClickListener(v -> openFilePicker());
        binding.btnBrowse.setOnClickListener(v ->
                startActivity(new Intent(this, FilePickerActivity.class)));
        binding.btnAbout.setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        if (binding.btnClearRecent != null) {
            binding.btnClearRecent.setOnClickListener(v -> {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"text/markdown", "text/x-markdown", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        UriPermissionUtils.addReadPersistableFlags(intent);
        openFileLauncher.launch(intent);
    }

    private void refreshRecentFiles() {
        List<RecentFilesManager.RecentEntry> list = RecentFilesManager.getRecentFiles(this);
        if (list.isEmpty()) {
            binding.recyclerRecentFiles.setVisibility(android.view.View.GONE);
            if (binding.layoutRecentHeader != null) binding.layoutRecentHeader.setVisibility(android.view.View.GONE);
            return;
        }
        binding.recyclerRecentFiles.setVisibility(android.view.View.VISIBLE);
        if (binding.layoutRecentHeader != null) binding.layoutRecentHeader.setVisibility(android.view.View.VISIBLE);

        recentFileAdapter.submitList(
                RecentFilesManager.limitRecentFiles(list, Constants.MAX_RECENT_DISPLAY));
    }

    private void onRecentFileClick(RecentFilesManager.RecentEntry entry) {
        try {
            Uri uri = Uri.parse(entry.uri);
            boolean hasPermission = UriPermissionUtils.takeReadPermission(getContentResolver(), uri);
            if (!hasPermission) {
                android.widget.Toast.makeText(this, R.string.error_no_permission, android.widget.Toast.LENGTH_SHORT).show();
            }
            Intent intent = new Intent(this, MarkdownActivity.class);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (SecurityException | IllegalArgumentException e) {
            android.widget.Toast.makeText(this, R.string.recent_file_invalid, android.widget.Toast.LENGTH_SHORT).show();
            RecentFilesManager.removeRecentFile(this, entry.uri);
            refreshRecentFiles();
        }
    }
}
