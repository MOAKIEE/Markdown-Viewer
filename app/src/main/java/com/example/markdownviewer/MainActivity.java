package com.example.markdownviewer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int PICK_FILE_REQUEST = 200;

    private static final int ACTION_NONE = 0;
    private static final int ACTION_OPEN_FILE = 1;
    private static final int ACTION_BROWSE = 2;

    private int pendingAction = ACTION_NONE;
    private LinearLayout recentContainer;
    private BlurView blurView;
    private FrameLayout backgroundContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SystemBarUtils.applyLightSystemBars(getWindow());

        blurView = findViewById(R.id.blur_view);
        backgroundContainer = findViewById(R.id.background_container);
        setupBlur();

        recentContainer = findViewById(R.id.recent_files_container);

        findViewById(R.id.btn_open_file).setOnClickListener(v -> {
            if (checkPermission()) {
                openFilePicker();
            } else {
                pendingAction = ACTION_OPEN_FILE;
                requestPermission();
            }
        });

        findViewById(R.id.btn_browse).setOnClickListener(v -> {
            if (checkPermission()) {
                startActivity(new Intent(this, FilePickerActivity.class));
            } else {
                pendingAction = ACTION_BROWSE;
                requestPermission();
            }
        });

        findViewById(R.id.btn_about).setOnClickListener(v -> {
            startActivity(new Intent(this, AboutActivity.class));
        });

        refreshRecentFiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecentFiles();
        if (pendingAction != ACTION_NONE && checkPermission()) {
            if (pendingAction == ACTION_OPEN_FILE) {
                openFilePicker();
            } else if (pendingAction == ACTION_BROWSE) {
                startActivity(new Intent(this, FilePickerActivity.class));
            }
            pendingAction = ACTION_NONE;
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

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingAction == ACTION_OPEN_FILE) {
                    openFilePicker();
                } else if (pendingAction == ACTION_BROWSE) {
                    startActivity(new Intent(this, FilePickerActivity.class));
                }
            } else {
                Toast.makeText(this, R.string.permission_required_storage, Toast.LENGTH_SHORT).show();
            }
            pendingAction = ACTION_NONE;
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"text/markdown", "text/x-markdown", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                RecentFilesManager.addRecentFile(this, uri);
                Intent intent = new Intent(this, MarkdownActivity.class);
                intent.setData(uri);
                startActivity(intent);
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = cursor.getString(idx);
                    }
                }
            }
        }
        if (result == null) {
            String path = uri.getLastPathSegment();
            if (path != null) {
                int lastSlash = path.lastIndexOf('/');
                result = lastSlash != -1 ? path.substring(lastSlash + 1) : path;
            }
        }
        return result != null ? result : "Untitled";
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
                displayName = getFileNameFromUri(Uri.parse(entry.uri));
            }
            tvName.setText(displayName);
            item.setOnClickListener(v -> {
                Intent intent = new Intent(this, MarkdownActivity.class);
                intent.setData(Uri.parse(entry.uri));
                startActivity(intent);
            });
            recentContainer.addView(item);
        }
    }
}
