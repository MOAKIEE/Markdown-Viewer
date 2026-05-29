package com.example.markdownviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.markdownviewer.databinding.ActivityFilePickerBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class FilePickerActivity extends AppCompatActivity implements FileAdapter.OnFileClickListener {

    private static final String TAG = "FilePicker";

    private ActivityFilePickerBinding binding;
    private FileAdapter fileAdapter;
    private Uri treeUri;
    private String currentPath;

    private final ArrayList<String> mPathStack = new ArrayList<>();
    private final AtomicInteger loadGeneration = new AtomicInteger(0);

    private final ActivityResultLauncher<Intent> treePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    treeUri = result.getData().getData();
                    if (treeUri != null) {
                        UriPermissionUtils.takeReadPermission(getContentResolver(), treeUri);
                        currentPath = DocumentsContract.getDocumentId(treeUri);
                        mPathStack.clear();
                        loadFilesAsync(treeUri, currentPath);
                    }
                } else {
                    if (treeUri == null) finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFilePickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SystemBarUtils.applySystemBarsForCurrentTheme(getWindow(), this);

        BlurHelper.setup(this, binding.blurView);

        binding.btnBack.setOnClickListener(v -> handleBack());
        binding.btnClose.setOnClickListener(v -> finish());

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileAdapter = new FileAdapter(this);
        binding.recyclerView.setAdapter(fileAdapter);

        SystemBarUtils.applyInsetsToView(binding.toolbarContainer, true, false);

        if (savedInstanceState != null && savedInstanceState.containsKey("tree_uri")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                treeUri = savedInstanceState.getParcelable("tree_uri", Uri.class);
            } else {
                treeUri = savedInstanceState.getParcelable("tree_uri");
            }
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
        binding = null;
    }

    private void openTreePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        UriPermissionUtils.addReadPersistableFlags(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_PROMPT, getString(R.string.file_picker_select_folder));
        }
        treePickerLauncher.launch(intent);
    }

    private void loadFilesAsync(Uri uri, String documentId) {
        currentPath = documentId;
        binding.tvPath.setText(extractDisplayName(documentId));
        binding.recyclerView.setVisibility(View.GONE);
        binding.emptyView.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.VISIBLE);

        final int generation = loadGeneration.incrementAndGet();

        AppExecutor.getInstance().diskIO().execute(() -> {
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
            } catch (Exception e) {
                Log.w(TAG, "Failed to query children", e);
            }

            Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            Collections.sort(mdList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            if (!mPathStack.isEmpty()) {
                String parentId = mPathStack.get(mPathStack.size() - 1);
                fileItems.add(new FileItem("..", parentId, true, true, 0));
            }
            fileItems.addAll(dirList);
            fileItems.addAll(mdList);

            AppExecutor.getInstance().mainThread().post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (generation != loadGeneration.get()) return;
                binding.progressBar.setVisibility(View.GONE);
                if (fileItems.isEmpty()) {
                    binding.recyclerView.setVisibility(View.GONE);
                    binding.emptyView.setVisibility(View.VISIBLE);
                } else {
                    binding.recyclerView.setAlpha(0f);
                    binding.recyclerView.setVisibility(View.VISIBLE);
                    binding.recyclerView.animate()
                            .alpha(1f)
                            .setDuration(Constants.ANIM_DURATION_APPEAR)
                            .setListener(null);
                    binding.emptyView.setVisibility(View.GONE);
                    fileAdapter.submitList(fileItems);
                    binding.recyclerView.scheduleLayoutAnimation();
                }
                updateBreadcrumbs();
            });
        });
    }

    private boolean isMarkdownFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown")
                || lower.endsWith(".mdown") || lower.endsWith(".mkd")
                || lower.endsWith(".mkdn") || lower.endsWith(".mdwn");
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
                handleBack();
            } else {
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
            String parentId = mPathStack.remove(mPathStack.size() - 1);
            loadFilesAsync(treeUri, parentId);
        } else {
            getOnBackPressedDispatcher().onBackPressed();
        }
    }

    private void updateBreadcrumbs() {
        if (binding.layoutBreadcrumbs == null || binding.scrollBreadcrumbs == null || treeUri == null) return;

        String rootDocId = DocumentsContract.getDocumentId(treeUri);
        if (mPathStack.isEmpty() && (currentPath == null || currentPath.equals(rootDocId))) {
            binding.scrollBreadcrumbs.setVisibility(View.GONE);
            binding.layoutBreadcrumbs.removeAllViews();
            return;
        }

        binding.scrollBreadcrumbs.setVisibility(View.VISIBLE);
        float scale = getResources().getDisplayMetrics().density;

        List<PathSegment> segments = new ArrayList<>();
        segments.add(new PathSegment(extractDisplayName(rootDocId), rootDocId, -1));

        for (int i = 0; i < mPathStack.size(); i++) {
            String path = mPathStack.get(i);
            if (!path.equals(rootDocId)) {
                segments.add(new PathSegment(extractDisplayName(path), path, i));
            }
        }

        if (currentPath != null && !currentPath.equals(rootDocId)) {
            segments.add(new PathSegment(extractDisplayName(currentPath), currentPath, -2));
        }

        int requiredChildCount = segments.size() * 2 - 1;
        int existingChildCount = binding.layoutBreadcrumbs.getChildCount();

        if (existingChildCount > requiredChildCount) {
            binding.layoutBreadcrumbs.removeViews(requiredChildCount, existingChildCount - requiredChildCount);
        }

        for (int i = 0; i < segments.size(); i++) {
            PathSegment segment = segments.get(i);
            int childIndex = i * 2;

            if (i > 0) {
                int dividerIndex = childIndex - 1;
                android.widget.ImageView ivDivider;
                if (dividerIndex < existingChildCount && binding.layoutBreadcrumbs.getChildAt(dividerIndex) instanceof android.widget.ImageView) {
                    ivDivider = (android.widget.ImageView) binding.layoutBreadcrumbs.getChildAt(dividerIndex);
                } else {
                    ivDivider = new android.widget.ImageView(this);
                    ivDivider.setImageResource(R.drawable.ic_chevron_right);
                    ivDivider.setImageTintList(android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.ios_text_secondary)));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            (int) (12 * scale + 0.5f), (int) (12 * scale + 0.5f));
                    lp.gravity = android.view.Gravity.CENTER_VERTICAL;
                    lp.leftMargin = (int) (4 * scale + 0.5f);
                    lp.rightMargin = (int) (4 * scale + 0.5f);
                    ivDivider.setLayoutParams(lp);
                    if (dividerIndex < binding.layoutBreadcrumbs.getChildCount()) {
                        binding.layoutBreadcrumbs.removeViewAt(dividerIndex);
                    }
                    binding.layoutBreadcrumbs.addView(ivDivider, dividerIndex);
                }
            }

            TextView tvSegment;
            if (childIndex < existingChildCount && binding.layoutBreadcrumbs.getChildAt(childIndex) instanceof TextView) {
                tvSegment = (TextView) binding.layoutBreadcrumbs.getChildAt(childIndex);
            } else {
                tvSegment = new TextView(this);
                tvSegment.setTextSize(13);
                tvSegment.setSingleLine(true);
                tvSegment.setGravity(android.view.Gravity.CENTER);
                tvSegment.setBackgroundResource(R.drawable.bg_breadcrumb_pill);
                int padLeftRight = (int) (12 * scale + 0.5f);
                int padTopBottom = (int) (6 * scale + 0.5f);
                tvSegment.setPadding(padLeftRight, padTopBottom, padLeftRight, padTopBottom);
                LinearLayout.LayoutParams lpPill = new LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                lpPill.leftMargin = (int) (2 * scale + 0.5f);
                lpPill.rightMargin = (int) (2 * scale + 0.5f);
                tvSegment.setLayoutParams(lpPill);
                if (childIndex < binding.layoutBreadcrumbs.getChildCount()) {
                    binding.layoutBreadcrumbs.removeViewAt(childIndex);
                }
                binding.layoutBreadcrumbs.addView(tvSegment, childIndex);
            }

            tvSegment.setText(segment.name);
            tvSegment.setOnClickListener(null);

            boolean isLast = (i == segments.size() - 1);
            if (isLast) {
                tvSegment.setTextColor(ContextCompat.getColor(this, R.color.ios_text_primary));
                tvSegment.setTypeface(null, android.graphics.Typeface.BOLD);
                tvSegment.setClickable(false);
                tvSegment.setFocusable(false);
            } else {
                tvSegment.setTextColor(ContextCompat.getColor(this, R.color.ios_blue));
                tvSegment.setTypeface(null, android.graphics.Typeface.NORMAL);
                tvSegment.setClickable(true);
                tvSegment.setFocusable(true);
                tvSegment.setOnClickListener(v -> {
                    if (segment.stackIndex == -1) {
                        mPathStack.clear();
                        loadFilesAsync(treeUri, segment.docId);
                    } else if (segment.stackIndex >= 0) {
                        int limit = segment.stackIndex;
                        while (mPathStack.size() > limit) {
                            mPathStack.remove(mPathStack.size() - 1);
                        }
                        loadFilesAsync(treeUri, segment.docId);
                    }
                });
            }
        }

        binding.scrollBreadcrumbs.post(() -> binding.scrollBreadcrumbs.fullScroll(View.FOCUS_RIGHT));
    }

    private static class PathSegment {
        private final String name;
        private final String docId;
        private final int stackIndex;
        PathSegment(String name, String docId, int stackIndex) {
            this.name = name;
            this.docId = docId;
            this.stackIndex = stackIndex;
        }
    }
}
