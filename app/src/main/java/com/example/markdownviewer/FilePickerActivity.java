package com.example.markdownviewer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilePickerActivity extends AppCompatActivity implements FileAdapter.OnFileClickListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    private File currentDirectory;
    private TextView tvPath;
    private View btnBack;
    private View emptyView;
    private View progressBar;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_picker);

        SystemBarUtils.applyLightSystemBars(getWindow());

        tvPath = findViewById(R.id.tv_path);
        btnBack = findViewById(R.id.btn_back);
        recyclerView = findViewById(R.id.recycler_view);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileAdapter = new FileAdapter(this);
        recyclerView.setAdapter(fileAdapter);

        btnBack.setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        SystemBarUtils.applyInsetsToView(findViewById(R.id.toolbar_container), true, false);

        if (checkPermission()) {
            loadFilesAsync(Environment.getExternalStorageDirectory());
        } else {
            requestPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermission() && currentDirectory == null) {
            loadFilesAsync(Environment.getExternalStorageDirectory());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadFilesAsync(Environment.getExternalStorageDirectory());
            } else {
                Toast.makeText(this, R.string.permission_required_browse, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadFilesAsync(File directory) {
        currentDirectory = directory;
        tvPath.setText(directory.getAbsolutePath());
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            List<FileItem> fileItems = new ArrayList<>();
            File[] files = directory.listFiles();

            if (files != null) {
                List<File> dirList = new ArrayList<>();
                List<File> mdList = new ArrayList<>();

                for (File file : files) {
                    if (file.isDirectory() && !file.getName().startsWith(".")) {
                        dirList.add(file);
                    } else if (file.getName().toLowerCase().endsWith(".md") ||
                            file.getName().toLowerCase().endsWith(".markdown")) {
                        mdList.add(file);
                    }
                }

                Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(mdList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                if (directory.getParentFile() != null) {
                    fileItems.add(new FileItem("..", true, true));
                }

                for (File dir : dirList) {
                    fileItems.add(new FileItem(dir.getName(), true, false));
                }

                for (File md : mdList) {
                    fileItems.add(new FileItem(md.getName(), false, false));
                }
            }

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                if (fileItems.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                } else {
                    recyclerView.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.GONE);
                    fileAdapter.setFiles(fileItems);
                }
            });
        });
    }

    @Override
    public void onFileClick(FileItem fileItem, int position) {
        if (fileItem.isDirectory()) {
            if (fileItem.isParent()) {
                if (currentDirectory.getParentFile() != null) {
                    loadFilesAsync(currentDirectory.getParentFile());
                }
            } else {
                File newDir = new File(currentDirectory, fileItem.getName());
                loadFilesAsync(newDir);
            }
        } else {
            File markdownFile = new File(currentDirectory, fileItem.getName());
            if (markdownFile.length() > 5 * 1024 * 1024) {
                Toast.makeText(this, R.string.file_picker_too_large, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, MarkdownActivity.class);
            Uri fileUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", markdownFile);
            RecentFilesManager.addRecentFile(this, fileUri);
            intent.setData(fileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra("file_name", fileItem.getName());
            startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        if (currentDirectory != null && !currentDirectory.equals(Environment.getExternalStorageDirectory()) && currentDirectory.getParentFile() != null) {
            loadFilesAsync(currentDirectory.getParentFile());
        } else {
            super.onBackPressed();
        }
    }
}
