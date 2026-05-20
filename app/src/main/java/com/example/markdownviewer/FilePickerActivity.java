package com.example.markdownviewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilePickerActivity extends AppCompatActivity implements FileAdapter.OnFileClickListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int PICK_TREE_REQUEST = 200;

    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    private Uri treeUri;
    private String currentPath;
    private TextView tvPath;
    private View emptyView;
    private View progressBar;
    
    // 💡 引入路径历史栈，储存历次进入的 documentId，解决异构 DocumentId 分割的崩溃与失效风险
    private final ArrayList<String> mPathStack = new ArrayList<>();
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_picker);

        SystemBarUtils.applyLightSystemBars(getWindow());

        tvPath = findViewById(R.id.tv_path);
        findViewById(R.id.btn_back).setOnClickListener(v -> handleBack());
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_view);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileAdapter = new FileAdapter(this);
        recyclerView.setAdapter(fileAdapter);

        SystemBarUtils.applyInsetsToView(findViewById(R.id.toolbar_container), true, false);

        if (savedInstanceState != null && savedInstanceState.containsKey("tree_uri")) {
            treeUri = savedInstanceState.getParcelable("tree_uri");
            currentPath = savedInstanceState.getString("current_path");
            ArrayList<String> savedStack = savedInstanceState.getStringArrayList("path_stack");
            if (savedStack != null) {
                mPathStack.addAll(savedStack);
            }
            if (treeUri != null) {
                loadFilesAsync(treeUri, currentPath);
                return;
            }
        }

        openTreePicker();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("tree_uri", treeUri);
        outState.putString("current_path", currentPath);
        outState.putStringArrayList("path_stack", mPathStack);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void openTreePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_PROMPT, getString(R.string.file_picker_select_folder));
        }
        startActivityForResult(intent, PICK_TREE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_TREE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            treeUri = data.getData();
            if (treeUri != null) {
                getContentResolver().takePersistableUriPermission(
                        treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                currentPath = DocumentsContract.getDocumentId(treeUri);
                mPathStack.clear(); // 选择新根目录时，清空历史栈
                loadFilesAsync(treeUri, currentPath);
            }
        } else if (requestCode == PICK_TREE_REQUEST) {
            if (treeUri == null) finish();
        }
    }

    private void loadFilesAsync(Uri uri, String documentId) {
        currentPath = documentId;
        tvPath.setText(extractDisplayName(documentId));
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            List<FileItem> fileItems = new ArrayList<>();
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId);

            List<FileItem> dirList = new ArrayList<>();
            List<FileItem> mdList = new ArrayList<>();

            try (android.database.Cursor cursor = getContentResolver().query(
                    childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE
                    },
                    null, null, null)) {

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String docId = cursor.getString(0);
                        String name = cursor.getString(1);
                        String mime = cursor.getString(2);
                        long size = cursor.isNull(3) ? 0 : cursor.getLong(3);

                        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                            dirList.add(new FileItem(name, docId, true, false, 0));
                        } else if (isMarkdownFile(name)) {
                            mdList.add(new FileItem(name, docId, false, false, size));
                        }
                    }
                }
            } catch (Exception ignored) {}

            Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            Collections.sort(mdList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            // 💡 使用目录栈的大小来判断是否展示“返回上级”选项，而不是判断 indexOf('/') 
            if (!mPathStack.isEmpty()) {
                String parentId = mPathStack.get(mPathStack.size() - 1);
                fileItems.add(new FileItem("..", parentId, true, true, 0));
            }
            fileItems.addAll(dirList);
            fileItems.addAll(mdList);

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

    private boolean isMarkdownFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }



    private String extractDisplayName(String documentId) {
        if (documentId == null) return "";
        int lastSlash = documentId.lastIndexOf('/');
        return lastSlash >= 0 ? documentId.substring(lastSlash + 1) : documentId;
    }

    @Override
    public void onFileClick(FileItem fileItem, int position) {
        if (fileItem.isDirectory()) {
            if (fileItem.isParent()) {
                handleBack(); // 💡 统一通过 handleBack 驱动出栈与加载
            } else {
                // 💡 进入下一级子目录：把上一级 currentPath 压入历史栈中，再加载新目录
                mPathStack.add(currentPath);
                loadFilesAsync(treeUri, fileItem.getDocumentId());
            }
        } else {
            if (fileItem.getSize() > Constants.MAX_FILE_SIZE) {
                Toast.makeText(this, R.string.file_picker_too_large, Toast.LENGTH_SHORT).show();
                return;
            }
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileItem.getDocumentId());
            RecentFilesManager.addRecentFile(this, docUri);
            Intent intent = new Intent(this, MarkdownActivity.class);
            intent.setData(docUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra("file_name", fileItem.getName());
            startActivity(intent);
        }
    }

    private void handleBack() {
        if (!mPathStack.isEmpty()) {
            // 💡 点击返回：从历史栈中弹出（移除）最上层，然后读取该目录 ID
            String parentId = mPathStack.remove(mPathStack.size() - 1);
            loadFilesAsync(treeUri, parentId);
        } else {
            super.onBackPressed();
        }
    }
}
