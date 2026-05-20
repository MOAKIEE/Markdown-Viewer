package com.example.markdownviewer;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration; // 💡 引入用于识别夜间模式
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.widget.ProgressBar;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.core.MarkwonTheme;
import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.glide.GlideImagesPlugin; // 💡 替换为 Glide 异步图层缓存加载器
import io.noties.markwon.ext.latex.JLatexMathPlugin; // 💡 引入 JLatexMath 渲染引擎
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin; // 💡 引入行内语法解析插件支持 LaTeX
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class MarkdownActivity extends AppCompatActivity {

    private static final int HIGHLIGHT_COLOR = 0xFFFFFF00;
    private static final int CURRENT_HIGHLIGHT_COLOR = 0xFFFFA500;

    private TextView markdownTextView;
    private TextView tvTitle;
    private Markwon markwon;
    private ScrollView scrollView;

    private View searchBar;
    private EditText etSearch;
    private TextView tvSearchCount;
    private View btnSearchPrev;
    private View btnSearchNext;
    private View btnSearchClose;

    private String rawMarkdownContent = "";
    private List<int[]> searchMatches = new ArrayList<>();
    private int currentMatchIndex = -1;
    private List<TocEntry> tocEntries = new ArrayList<>();
    private ProgressBar progressLoading;

    // 💡 引入搜索防抖 Handler 与延时 Runnable
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable = null;

    // 💡 记录当前的滚动历史及 Uri
    private int savedScrollY = 0;
    private Uri currentFileUri = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown);

        SystemBarUtils.applyLightSystemBars(getWindow());

        tvTitle = findViewById(R.id.tv_title);
        markdownTextView = findViewById(R.id.markdown_text);
        scrollView = findViewById(R.id.scroll_view);
        progressLoading = findViewById(R.id.progress_loading);

        // 💡 自适应系统深浅色主题，微调超链接渲染对比度
        boolean isDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        markwon = Markwon.builder(this)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(GlideImagesPlugin.create(this)) // 💡 Glide 大图异步缓存托管，杜绝 OOM
                .usePlugin(MarkwonInlineParserPlugin.create()) // 💡 支持 LaTeX 行内公式解析
                .usePlugin(JLatexMathPlugin.create(markdownTextView.getTextSize(), builder -> {
                    builder.inlinesEnabled(true); // 💡 启用行内 LaTeX 渲染支持
                }))
                .usePlugin(TablePlugin.create(this))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        // 1. 设置代码块和行内代码背景色为温和的半透明灰色，亮暗色自适应
                        builder.codeBackgroundColor(0x1A808080); // 10% 灰色
                        builder.codeBlockBackgroundColor(0x10808080); // 6% 灰色
                        
                        // 2. 强化块引用（Blockquotes）的边界线宽度，并设定温和的半透明线条颜色
                        builder.blockQuoteWidth(12); // 宽度设定为 12px (约 4dp)
                        builder.blockQuoteColor(0x30808080); // 线条颜色设定为温和带半透明的灰色

                        // 3. 💡 夜间护眼色彩自适应
                        if (isDarkMode) {
                            builder.linkColor(0xFF64B5F6); // 护眼亮蓝色
                        } else {
                            builder.linkColor(0xFF1E88E5); // 经典深蓝色
                        }
                    }
                })
                .build();

        findViewById(R.id.btn_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.btn_search).setOnClickListener(v -> showSearchBar());
        findViewById(R.id.btn_toc).setOnClickListener(v -> showTocDialog());

        SystemBarUtils.applyInsetsToView(findViewById(R.id.toolbar_container), true, false);

        searchBar = findViewById(R.id.search_bar);
        etSearch = findViewById(R.id.et_search);
        tvSearchCount = findViewById(R.id.tv_search_count);
        btnSearchPrev = findViewById(R.id.btn_search_prev);
        btnSearchNext = findViewById(R.id.btn_search_next);
        btnSearchClose = findViewById(R.id.btn_search_close);

        btnSearchClose.setOnClickListener(v -> hideSearchBar());
        btnSearchNext.setOnClickListener(v -> nextMatch());
        btnSearchPrev.setOnClickListener(v -> prevMatch());

        // 💡 搜索输入框绑定 300ms 防抖
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> performSearch();
                searchHandler.postDelayed(searchRunnable, 300);
            }
        });

        // 💡 点击软键盘确认立即执行搜索
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                performSearch();
                return true;
            }
            return false;
        });

        String filePath = getIntent().getStringExtra("file_path");
        String fileName = getIntent().getStringExtra("file_name");
        Uri fileUri = getIntent().getData();

        if (fileName != null) {
            tvTitle.setText(fileName);
        } else if (fileUri != null) {
            tvTitle.setText(FileUtils.getDisplayName(this, fileUri));
        }

        if (filePath != null) {
            loadMarkdownFile(filePath);
        } else if (fileUri != null) {
            loadMarkdownFromUri(fileUri);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 💡 退出或切后台时，即时自动捕获当前 ScrollY 高度持久化存盘，实现无感恢复
        if (scrollView != null && currentFileUri != null) {
            int scrollY = scrollView.getScrollY();
            RecentFilesManager.updateScrollY(this, currentFileUri, scrollY);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
    }

    private void showTocDialog() {
        if (tocEntries.isEmpty()) {
            Toast.makeText(this, R.string.toc_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_toc, null);
        ListView listView = dialogView.findViewById(R.id.list_toc);
        TocAdapter adapter = new TocAdapter();
        listView.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        listView.setOnItemClickListener((parent, view, position, id) -> {
            TocEntry entry = tocEntries.get(position);
            scrollToLine(entry.lineIndex);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void scrollToLine(int lineIndex) {
        CharSequence text = markdownTextView.getText();
        if (text == null) return;
        Layout layout = markdownTextView.getLayout();
        if (layout == null) return;
        int offset = 0;
        int currentLine = 0;
        for (int i = 0; i < text.length() && currentLine < lineIndex; i++) {
            if (text.charAt(i) == '\n') currentLine++;
            offset++;
        }
        int line = layout.getLineForOffset(offset);
        int y = layout.getLineTop(line);
        scrollView.smoothScrollTo(0, y + markdownTextView.getTop());
    }

    private void extractToc(String content) {
        tocEntries.clear();
        String[] lines = content.split("\n");
        Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$");
        boolean isInCodeBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String trimmedLine = lines[i].trim();
            if (trimmedLine.startsWith("```")) {
                isInCodeBlock = !isInCodeBlock;
                continue;
            }
            if (isInCodeBlock) {
                continue;
            }
            Matcher m = headingPattern.matcher(trimmedLine);
            if (m.find()) {
                int level = m.group(1).length();
                String title = m.group(2).trim();
                tocEntries.add(new TocEntry(title, level, i));
            }
        }
    }

    private static class TocEntry {
        final String title;
        final int level;
        final int lineIndex;
        TocEntry(String title, int level, int lineIndex) {
            this.title = title;
            this.level = level;
            this.lineIndex = lineIndex;
        }
    }

    private class TocAdapter extends BaseAdapter {
        @Override public int getCount() { return tocEntries.size(); }
        @Override public Object getItem(int position) { return tocEntries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(MarkdownActivity.this).inflate(R.layout.item_toc, parent, false);
                holder = new ViewHolder();
                holder.tvTitle = convertView.findViewById(R.id.tv_toc_title);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            TocEntry entry = tocEntries.get(position);
            String indent = "";
            for (int i = 1; i < entry.level; i++) indent += "    ";
            holder.tvTitle.setText(indent + entry.title);
            return convertView;
        }
        class ViewHolder { TextView tvTitle; }
    }

    private void showSearchBar() {
        searchBar.setVisibility(View.VISIBLE);
        etSearch.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSearchBar() {
        searchBar.setVisibility(View.GONE);
        clearHighlights();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
    }

    private void performSearch() {
        String query = etSearch.getText().toString().toLowerCase();
        if (query.isEmpty()) {
            clearHighlights();
            return;
        }

        CharSequence text = markdownTextView.getText();
        if (text == null) return;
        String src = text.toString().toLowerCase();

        searchMatches.clear();
        currentMatchIndex = -1;

        int index = src.indexOf(query);
        while (index >= 0) {
            searchMatches.add(new int[]{index, index + query.length()});
            index = src.indexOf(query, index + 1);
        }

        if (searchMatches.isEmpty()) {
            tvSearchCount.setText("0/0");
            clearHighlights();
            return;
        }

        currentMatchIndex = 0;
        applyHighlights();
        updateSearchCount();
        scrollToMatch(0);
    }

    private void nextMatch() {
        if (searchMatches.isEmpty()) {
            performSearch();
            return;
        }
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size();
        applyHighlights();
        updateSearchCount();
        scrollToMatch(currentMatchIndex);
    }

    private void prevMatch() {
        if (searchMatches.isEmpty()) {
            performSearch();
            return;
        }
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
        applyHighlights();
        updateSearchCount();
        scrollToMatch(currentMatchIndex);
    }

    private void updateSearchCount() {
        if (searchMatches.isEmpty()) {
            tvSearchCount.setText("0/0");
        } else {
            tvSearchCount.setText((currentMatchIndex + 1) + "/" + searchMatches.size());
        }
    }

    private void applyHighlights() {
        CharSequence text = markdownTextView.getText();
        if (text == null || !(text instanceof Spannable)) return;
        Spannable spannable = (Spannable) text;

        BackgroundColorSpan[] spans = spannable.getSpans(0, spannable.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : spans) {
            spannable.removeSpan(span);
        }

        for (int i = 0; i < searchMatches.size(); i++) {
            int[] match = searchMatches.get(i);
            int color = (i == currentMatchIndex) ? CURRENT_HIGHLIGHT_COLOR : HIGHLIGHT_COLOR;
            spannable.setSpan(new BackgroundColorSpan(color), match[0], match[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void clearHighlights() {
        CharSequence text = markdownTextView.getText();
        if (text == null || !(text instanceof Spannable)) return;
        Spannable spannable = (Spannable) text;
        BackgroundColorSpan[] spans = spannable.getSpans(0, spannable.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : spans) {
            spannable.removeSpan(span);
        }
        searchMatches.clear();
        currentMatchIndex = -1;
        tvSearchCount.setText("");
    }

    private void scrollToMatch(int matchIndex) {
        if (matchIndex < 0 || matchIndex >= searchMatches.size()) return;
        int[] match = searchMatches.get(matchIndex);
        int lineStart = match[0];
        CharSequence text = markdownTextView.getText();
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        Layout layout = markdownTextView.getLayout();
        if (layout != null) {
            int line = layout.getLineForOffset(match[0]);
            int y = layout.getLineTop(line);
            scrollView.smoothScrollTo(0, y + markdownTextView.getTop());
        }
    }

    private void loadMarkdownFile(String filePath) {
        File file;
        try {
            file = new File(filePath).getCanonicalFile();
        } catch (IOException e) {
            Toast.makeText(this, R.string.error_invalid_path, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!file.exists()) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        if (file.length() > Constants.MAX_FILE_SIZE) {
            Toast.makeText(this, R.string.error_file_too_large, Toast.LENGTH_SHORT).show();
            return;
        }

        // 💡 记录当前文件 Uri 并读取历史滚动高度
        currentFileUri = Uri.fromFile(file);
        savedScrollY = RecentFilesManager.getScrollY(this, currentFileUri);

        progressLoading.setAlpha(1f);
        progressLoading.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);

        AppExecutor.getInstance().diskIO().execute(() -> {
            StringBuilder content = new StringBuilder();
            boolean readSuccess = true;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            } catch (IOException e) {
                readSuccess = false;
            }

            if (!readSuccess) {
                AppExecutor.getInstance().mainThread().post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressLoading.setVisibility(View.GONE);
                    scrollView.setAlpha(1f);
                    scrollView.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.error_read_failed, Toast.LENGTH_SHORT).show();
                });
                return;
            }

            final String markdown = content.toString();
            extractToc(markdown);
            final Spanned spanned = markwon.toMarkdown(markdown);

            AppExecutor.getInstance().mainThread().post(() -> {
                if (isFinishing() || isDestroyed()) return;
                rawMarkdownContent = markdown;
                markwon.setParsedMarkdown(markdownTextView, spanned);
                RecentFilesManager.addRecentFile(this, currentFileUri);
                
                // 💡 优雅平滑的渐隐淡出与渐现动效
                progressLoading.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                    progressLoading.setVisibility(View.GONE);
                    progressLoading.setAlpha(1f);
                });
                scrollView.setAlpha(0f);
                scrollView.setVisibility(View.VISIBLE);
                scrollView.animate().alpha(1f).setDuration(250).setListener(null);

                // 💡 异步恢复滚动高度
                if (savedScrollY > 0) {
                    scrollView.post(() -> scrollView.scrollTo(0, savedScrollY));
                }
            });
        });
    }

    private void loadMarkdownFromUri(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (!"content".equals(scheme) && !"file".equals(scheme)) {
            Toast.makeText(this, R.string.error_unsupported_source, Toast.LENGTH_SHORT).show();
            return;
        }

        // 💡 记录当前文件 Uri 并读取历史滚动高度
        currentFileUri = uri;
        savedScrollY = RecentFilesManager.getScrollY(this, currentFileUri);

        progressLoading.setAlpha(1f);
        progressLoading.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);

        AppExecutor.getInstance().diskIO().execute(() -> {
            long fileSize = 0;
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx);
                    }
                }
            } catch (Exception ignored) {}

            if (fileSize > Constants.MAX_FILE_SIZE) {
                AppExecutor.getInstance().mainThread().post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressLoading.setVisibility(View.GONE);
                    scrollView.setAlpha(1f);
                    scrollView.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.error_file_too_large, Toast.LENGTH_SHORT).show();
                });
                return;
            }

            StringBuilder content = new StringBuilder();
            boolean success = false;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line).append("\n");
                        }
                        success = true;
                    }
                }
            } catch (IOException e) {
                success = false;
            }

            if (!success) {
                AppExecutor.getInstance().mainThread().post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressLoading.setVisibility(View.GONE);
                    scrollView.setAlpha(1f);
                    scrollView.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.error_read_failed, Toast.LENGTH_SHORT).show();
                });
                return;
            }

            final String markdown = content.toString();
            extractToc(markdown);
            final Spanned spanned = markwon.toMarkdown(markdown);

            AppExecutor.getInstance().mainThread().post(() -> {
                if (isFinishing() || isDestroyed()) return;
                rawMarkdownContent = markdown;
                markwon.setParsedMarkdown(markdownTextView, spanned);
                RecentFilesManager.addRecentFile(this, uri);
                
                // 💡 优雅平滑的渐隐淡出与渐现动效
                progressLoading.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                    progressLoading.setVisibility(View.GONE);
                    progressLoading.setAlpha(1f);
                });
                scrollView.setAlpha(0f);
                scrollView.setVisibility(View.VISIBLE);
                scrollView.animate().alpha(1f).setDuration(250).setListener(null);

                // 💡 异步恢复滚动高度
                if (savedScrollY > 0) {
                    scrollView.post(() -> scrollView.scrollTo(0, savedScrollY));
                }
            });
        });
    }
}
