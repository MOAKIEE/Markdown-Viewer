package com.example.markdownviewer;

import android.app.AlertDialog;
import android.content.Intent;
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
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class MarkdownActivity extends AppCompatActivity {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown);

        SystemBarUtils.applyLightSystemBars(getWindow());

        tvTitle = findViewById(R.id.tv_title);
        markdownTextView = findViewById(R.id.markdown_text);
        scrollView = findViewById(R.id.scroll_view);

        markwon = Markwon.builder(this)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(ImagesPlugin.create())
                .usePlugin(TablePlugin.create(this))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
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

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { performSearch(); }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
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
            tvTitle.setText(getFileNameFromUri(fileUri));
        }

        if (filePath != null) {
            loadMarkdownFile(filePath);
        } else if (fileUri != null) {
            loadMarkdownFromUri(fileUri);
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
        for (int i = 0; i < lines.length; i++) {
            Matcher m = headingPattern.matcher(lines[i].trim());
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

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri != null && "content".equals(uri.getScheme())) {
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
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "Untitled";
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
        if (file.length() > MAX_FILE_SIZE) {
            Toast.makeText(this, R.string.error_file_too_large, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            Toast.makeText(this, R.string.error_read_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        rawMarkdownContent = content.toString();
        extractToc(rawMarkdownContent);
        markwon.setMarkdown(markdownTextView, rawMarkdownContent);
        RecentFilesManager.addRecentFile(this, Uri.fromFile(file));
    }

    private void loadMarkdownFromUri(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (!"content".equals(scheme) && !"file".equals(scheme)) {
            Toast.makeText(this, R.string.error_unsupported_source, Toast.LENGTH_SHORT).show();
            return;
        }

        long fileSize = 0;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    fileSize = cursor.getLong(sizeIdx);
                }
            }
        } catch (Exception ignored) {}

        if (fileSize > MAX_FILE_SIZE) {
            Toast.makeText(this, R.string.error_file_too_large, Toast.LENGTH_SHORT).show();
            return;
        }

        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                Toast.makeText(this, R.string.error_open_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            rawMarkdownContent = content.toString();
            extractToc(rawMarkdownContent);
            markwon.setMarkdown(markdownTextView, rawMarkdownContent);
            RecentFilesManager.addRecentFile(this, uri);
        } catch (IOException e) {
            Toast.makeText(this, R.string.error_read_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
