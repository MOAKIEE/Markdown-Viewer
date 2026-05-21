package com.example.markdownviewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.noties.markwon.Markwon;

public class MarkdownActivity extends AppCompatActivity {

    private static final String TAG = "MarkdownActivity";

    private TextView markdownTextView;
    private TextView tvTitle;
    private Markwon markwon;
    private ScrollView scrollView;
    private ProgressBar progressLoading;

    private View searchBar;
    private EditText etSearch;

    private String rawMarkdownContent = "";
    private List<TocParser.TocEntry> tocEntries;
    private SearchHelper searchHelper;

    private int savedScrollY = 0;
    private Uri currentFileUri = null;

    private SharedPreferences readerPrefs;
    private int currentThemeMode = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown);

        SystemBarUtils.applyLightSystemBars(getWindow());

        readerPrefs = getSharedPreferences(Constants.PREFS_READER_CONFIG, MODE_PRIVATE);
        currentThemeMode = readerPrefs.getInt("theme_mode", 0);

        tvTitle = findViewById(R.id.tv_title);
        markdownTextView = findViewById(R.id.markdown_text);
        scrollView = findViewById(R.id.scroll_view);
        progressLoading = findViewById(R.id.progress_loading);

        int savedFontSize = readerPrefs.getInt("font_size", Constants.FONT_SIZE_DEFAULT);
        markdownTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedFontSize);

        float savedLineSpacing = readerPrefs.getFloat("line_spacing", Constants.LINE_SPACING_DEFAULT);
        applyReaderTheme(currentThemeMode, savedLineSpacing);

        findViewById(R.id.btn_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.btn_search).setOnClickListener(v -> showSearchBar());
        findViewById(R.id.btn_toc).setOnClickListener(v -> showTocDialog());
        findViewById(R.id.btn_settings).setOnClickListener(v -> showReaderConfigDialog());

        SystemBarUtils.applyInsetsToView(findViewById(R.id.toolbar_container), true, false);

        searchBar = findViewById(R.id.search_bar);
        etSearch = findViewById(R.id.et_search);
        TextView tvSearchCount = findViewById(R.id.tv_search_count);

        searchHelper = new SearchHelper(markdownTextView, etSearch, tvSearchCount,
                ReaderTheme.getHighlightColor(this, currentThemeMode),
                ReaderTheme.getCurrentHighlightColor(this, currentThemeMode));
        searchHelper.attachToEditText();

        findViewById(R.id.btn_search_close).setOnClickListener(v -> hideSearchBar());
        findViewById(R.id.btn_search_next).setOnClickListener(v -> searchHelper.nextMatch());
        findViewById(R.id.btn_search_prev).setOnClickListener(v -> searchHelper.prevMatch());

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchHelper.performSearch();
                return true;
            }
            return false;
        });

        Uri fileUri = getIntent().getData();
        String fileName = getIntent().getStringExtra("file_name");

        if (fileName != null) {
            tvTitle.setText(fileName);
        } else if (fileUri != null) {
            tvTitle.setText(FileUtils.getDisplayName(this, fileUri));
        }

        if (fileUri != null) {
            loadMarkdownFromUri(fileUri);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scrollView != null && currentFileUri != null) {
            RecentFilesManager.updateScrollY(this, currentFileUri, scrollView.getScrollY());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchHelper != null) searchHelper.destroy();
    }

    // ---- Theme ----

    private void applyReaderTheme(int themeMode, float lineSpacing) {
        boolean dark = ReaderTheme.isDarkMode(this);
        int bgColor = ReaderTheme.getBgColor(this, themeMode);
        int cardColor = ReaderTheme.getCardColor(this, themeMode);
        int textColor = ReaderTheme.getTextColor(this, themeMode);
        int toolbarBgColor = ReaderTheme.getToolbarColor(this, themeMode);
        int hintColor = ReaderTheme.getHintColor(this, themeMode);

        applyColorsToViews(bgColor, cardColor, textColor, toolbarBgColor, hintColor);
        markdownTextView.setLineSpacing(0, lineSpacing);

        markwon = MarkwonFactory.create(this, markdownTextView, themeMode);
        if (rawMarkdownContent != null && !rawMarkdownContent.isEmpty()) {
            Spanned spanned = markwon.toMarkdown(rawMarkdownContent);
            markwon.setParsedMarkdown(markdownTextView, spanned);
        }

        if (ReaderTheme.useLightStatusBar(themeMode) && !dark) {
            SystemBarUtils.applyLightSystemBars(getWindow());
        } else {
            SystemBarUtils.applyDarkSystemBars(getWindow());
        }

        if (searchHelper != null) {
            searchHelper.setColors(
                    ReaderTheme.getHighlightColor(this, themeMode),
                    ReaderTheme.getCurrentHighlightColor(this, themeMode));
        }
    }

    private void applyColorsToViews(int bgColor, int cardColor, int textColor, int toolbarBgColor, int hintColor) {
        scrollView.setBackgroundColor(bgColor);
        ViewParent parent = scrollView.getParent();
        if (parent instanceof View) {
            ((View) parent).setBackgroundColor(bgColor);
        }

        androidx.cardview.widget.CardView cardView =
                (androidx.cardview.widget.CardView) markdownTextView.getParent();
        if (cardView != null) {
            cardView.setCardBackgroundColor(cardColor);
        }

        markdownTextView.setTextColor(textColor);
        tvTitle.setTextColor(textColor);

        View toolbarContainer = findViewById(R.id.toolbar_container);
        if (toolbarContainer != null) {
            toolbarContainer.setBackgroundColor(toolbarBgColor);
        }

        int iconTint = textColor;
        int[] btnIds = {R.id.btn_back, R.id.btn_toc, R.id.btn_search, R.id.btn_settings};
        for (int id : btnIds) {
            android.widget.ImageView btn = findViewById(id);
            if (btn != null) btn.setColorFilter(iconTint);
        }

        View searchBarView = findViewById(R.id.search_bar);
        if (searchBarView != null) {
            searchBarView.setBackgroundColor(toolbarBgColor);
        }
        EditText etSearchField = findViewById(R.id.et_search);
        if (etSearchField != null) {
            etSearchField.setTextColor(textColor);
            etSearchField.setHintTextColor(hintColor);
        }
        int[] searchBtnIds = {R.id.btn_search_prev, R.id.btn_search_next, R.id.btn_search_close};
        for (int id : searchBtnIds) {
            android.widget.ImageView btn = findViewById(id);
            if (btn != null) btn.setColorFilter(iconTint);
        }
    }

    // ---- Reader Config Dialog ----

    private void showReaderConfigDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reader_config, null);

        View btnDecrease = dialogView.findViewById(R.id.btn_size_decrease);
        View btnIncrease = dialogView.findViewById(R.id.btn_size_increase);
        TextView tvCurrentSize = dialogView.findViewById(R.id.tv_current_size);

        final int[] currentSize = {readerPrefs.getInt("font_size", Constants.FONT_SIZE_DEFAULT)};
        tvCurrentSize.setText(getString(R.string.reader_font_size_default_format, currentSize[0]));

        btnDecrease.setOnClickListener(v -> {
            if (currentSize[0] > Constants.FONT_SIZE_MIN) {
                currentSize[0]--;
                tvCurrentSize.setText(getString(R.string.reader_font_size_default_format, currentSize[0]));
                markdownTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSize[0]);
                readerPrefs.edit().putInt("font_size", currentSize[0]).apply();
            } else {
                Toast.makeText(this, R.string.error_font_size_min, Toast.LENGTH_SHORT).show();
            }
        });

        btnIncrease.setOnClickListener(v -> {
            if (currentSize[0] < Constants.FONT_SIZE_MAX) {
                currentSize[0]++;
                tvCurrentSize.setText(getString(R.string.reader_font_size_default_format, currentSize[0]));
                markdownTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSize[0]);
                readerPrefs.edit().putInt("font_size", currentSize[0]).apply();
            } else {
                Toast.makeText(this, R.string.error_font_size_max, Toast.LENGTH_SHORT).show();
            }
        });

        // Line spacing
        View btnSpacing12 = dialogView.findViewById(R.id.btn_spacing_12);
        View btnSpacing14 = dialogView.findViewById(R.id.btn_spacing_14);
        View btnSpacing16 = dialogView.findViewById(R.id.btn_spacing_16);

        Runnable updateSpacingHighlight = () -> {
            float spacing = readerPrefs.getFloat("line_spacing", Constants.LINE_SPACING_DEFAULT);
            int activeColor = ContextCompat.getColor(this, R.color.spacing_highlight_active);
            int inactiveColor = ContextCompat.getColor(this, R.color.spacing_highlight_inactive);
            btnSpacing12.setBackgroundColor(Math.abs(spacing - 1.2f) < 0.05f ? activeColor : inactiveColor);
            btnSpacing14.setBackgroundColor(Math.abs(spacing - 1.4f) < 0.05f ? activeColor : inactiveColor);
            btnSpacing16.setBackgroundColor(Math.abs(spacing - 1.6f) < 0.05f ? activeColor : inactiveColor);
        };
        updateSpacingHighlight.run();

        View.OnClickListener spacingListener = v -> {
            float newSpacing;
            if (v.getId() == R.id.btn_spacing_12) newSpacing = 1.2f;
            else if (v.getId() == R.id.btn_spacing_14) newSpacing = 1.4f;
            else newSpacing = 1.6f;
            readerPrefs.edit().putFloat("line_spacing", newSpacing).apply();
            updateSpacingHighlight.run();
            applyReaderTheme(readerPrefs.getInt("theme_mode", 0), newSpacing);
        };
        btnSpacing12.setOnClickListener(spacingListener);
        btnSpacing14.setOnClickListener(spacingListener);
        btnSpacing16.setOnClickListener(spacingListener);

        // Theme
        androidx.cardview.widget.CardView btnClassic = dialogView.findViewById(R.id.theme_classic);
        androidx.cardview.widget.CardView btnSepia = dialogView.findViewById(R.id.theme_sepia);
        androidx.cardview.widget.CardView btnGreen = dialogView.findViewById(R.id.theme_green);
        androidx.cardview.widget.CardView btnSpace = dialogView.findViewById(R.id.theme_space);

        Runnable updateThemeHighlight = () -> {
            int theme = readerPrefs.getInt("theme_mode", 0);
            btnClassic.setCardElevation(theme == 0 ? 12f : 0f);
            btnSepia.setCardElevation(theme == 1 ? 12f : 0f);
            btnGreen.setCardElevation(theme == 2 ? 12f : 0f);
            btnSpace.setCardElevation(theme == 3 ? 12f : 0f);

            btnClassic.setForeground(theme == 0 ? getDrawable(R.drawable.glass_card_stroke) : null);
            btnSepia.setForeground(theme == 1 ? getDrawable(R.drawable.glass_card_stroke) : null);
            btnGreen.setForeground(theme == 2 ? getDrawable(R.drawable.glass_card_stroke) : null);
            btnSpace.setForeground(theme == 3 ? getDrawable(R.drawable.glass_card_stroke) : null);
        };
        updateThemeHighlight.run();

        View.OnClickListener themeListener = v -> {
            int newTheme;
            if (v.getId() == R.id.theme_classic) newTheme = 0;
            else if (v.getId() == R.id.theme_sepia) newTheme = 1;
            else if (v.getId() == R.id.theme_green) newTheme = 2;
            else newTheme = 3;
            readerPrefs.edit().putInt("theme_mode", newTheme).apply();
            currentThemeMode = newTheme;
            updateThemeHighlight.run();
            float spacing = readerPrefs.getFloat("line_spacing", Constants.LINE_SPACING_DEFAULT);
            applyReaderTheme(newTheme, spacing);
        };
        btnClassic.setOnClickListener(themeListener);
        btnSepia.setOnClickListener(themeListener);
        btnGreen.setOnClickListener(themeListener);
        btnSpace.setOnClickListener(themeListener);

        dialog.setContentView(dialogView);
        dialog.show();
    }

    // ---- TOC ----

    private void showTocDialog() {
        if (tocEntries == null || tocEntries.isEmpty()) {
            Toast.makeText(this, R.string.toc_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_toc, null);
        ListView listView = dialogView.findViewById(R.id.list_toc);
        TocAdapter adapter = new TocAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            TocParser.TocEntry entry = tocEntries.get(position);
            scrollToLine(entry.lineIndex);
            dialog.dismiss();
        });
        dialog.setContentView(dialogView);
        dialog.show();
    }

    private void scrollToLine(int lineIndex) {
        CharSequence text = markdownTextView.getText();
        if (text == null) return;
        android.text.Layout layout = markdownTextView.getLayout();
        if (layout == null) return;
        int offset = 0;
        int currentLine = 0;
        for (int i = 0; i < text.length() && currentLine < lineIndex; i++) {
            if (text.charAt(i) == '\n') currentLine++;
            offset++;
        }
        int line = layout.getLineForOffset(offset);
        int y = layout.getLineTop(line);

        int scrollViewHeight = scrollView.getHeight();
        int targetY = y + markdownTextView.getTop() - (scrollViewHeight / 3);
        if (targetY < 0) targetY = 0;

        scrollView.smoothScrollTo(0, targetY);
    }

    private class TocAdapter extends BaseAdapter {
        private final float density = getResources().getDisplayMetrics().density;

        @Override public int getCount() { return tocEntries.size(); }
        @Override public Object getItem(int position) { return tocEntries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(MarkdownActivity.this)
                        .inflate(R.layout.item_toc, parent, false);
                holder = new ViewHolder();
                holder.tvTitle = convertView.findViewById(R.id.tv_toc_title);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            TocParser.TocEntry entry = tocEntries.get(position);

            int paddingLeftPx = (int) (((entry.level - 1) * 16 + 8) * density + 0.5f);
            int paddingTopBottomPx = (int) (12 * density + 0.5f);
            int paddingRightPx = (int) (8 * density + 0.5f);
            convertView.setPadding(paddingLeftPx, paddingTopBottomPx, paddingRightPx, paddingTopBottomPx);

            if (entry.level == 1) {
                holder.tvTitle.setTextSize(16.5f);
                holder.tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.tvTitle.setTextColor(ContextCompat.getColor(MarkdownActivity.this, R.color.ios_text_primary));
            } else if (entry.level == 2) {
                holder.tvTitle.setTextSize(14.5f);
                holder.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.tvTitle.setTextColor(ContextCompat.getColor(MarkdownActivity.this, R.color.ios_text_primary));
            } else {
                holder.tvTitle.setTextSize(13.0f);
                holder.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.tvTitle.setTextColor(ContextCompat.getColor(MarkdownActivity.this, R.color.ios_text_secondary));
            }

            holder.tvTitle.setText(entry.title);
            return convertView;
        }
        class ViewHolder { TextView tvTitle; }
    }

    // ---- Search UI ----

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
        searchHelper.clearHighlights();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
    }

    // ---- File Loading ----

    private void loadMarkdownFromUri(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (!"content".equals(scheme) && !"file".equals(scheme)) {
            Toast.makeText(this, R.string.error_unsupported_source, Toast.LENGTH_SHORT).show();
            return;
        }

        currentFileUri = uri;
        savedScrollY = RecentFilesManager.getScrollY(this, currentFileUri);

        showLoadingState();

        AppExecutor.getInstance().diskIO().execute(() -> {
            long fileSize = 0;
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to query file size for URI: " + uri, e);
            }

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
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line).append("\n");
                        }
                        success = true;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to read URI: " + uri, e);
            }

            if (!success) {
                showLoadingError();
                return;
            }

            final String markdown = content.toString();
            final List<TocParser.TocEntry> toc = TocParser.parse(markdown);

            AppExecutor.getInstance().mainThread().post(() -> {
                if (isFinishing() || isDestroyed()) return;
                tocEntries = toc;
                rawMarkdownContent = markdown;
                Spanned spanned = markwon.toMarkdown(markdown);
                renderContent(spanned, uri);
            });
        });
    }

    private void showLoadingState() {
        progressLoading.setAlpha(1f);
        progressLoading.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);
    }

    private void showLoadingError() {
        AppExecutor.getInstance().mainThread().post(() -> {
            if (isFinishing() || isDestroyed()) return;
            progressLoading.setVisibility(View.GONE);
            scrollView.setAlpha(1f);
            scrollView.setVisibility(View.VISIBLE);
            Toast.makeText(this, R.string.error_read_failed, Toast.LENGTH_SHORT).show();
        });
    }

    private void renderContent(Spanned spanned, Uri uri) {
        markwon.setParsedMarkdown(markdownTextView, spanned);
        RecentFilesManager.addRecentFile(this, uri);

        progressLoading.animate().alpha(0f).setDuration(Constants.ANIM_DURATION_FADE).withEndAction(() -> {
            progressLoading.setVisibility(View.GONE);
            progressLoading.setAlpha(1f);
        });
        scrollView.setAlpha(0f);
        scrollView.setVisibility(View.VISIBLE);
        scrollView.animate().alpha(1f).setDuration(Constants.ANIM_DURATION_APPEAR).setListener(null);

        if (savedScrollY > 0) {
            scrollView.post(() -> scrollView.scrollTo(0, savedScrollY));
        }
    }
}
