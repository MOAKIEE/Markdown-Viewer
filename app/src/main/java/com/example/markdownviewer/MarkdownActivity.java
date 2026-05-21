package com.example.markdownviewer;

import android.app.AlertDialog;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
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
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.widget.ProgressBar;
import android.util.TypedValue;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.core.MarkwonTheme;
import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
import io.noties.markwon.image.glide.GlideImagesPlugin;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class MarkdownActivity extends AppCompatActivity {

    private static final String TAG = "MarkdownActivity";
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

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable = null;

    private int savedScrollY = 0;
    private Uri currentFileUri = null;

    private SharedPreferences readerPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown);

        SystemBarUtils.applyLightSystemBars(getWindow());

        readerPrefs = getSharedPreferences(Constants.PREFS_READER_CONFIG, MODE_PRIVATE);

        tvTitle = findViewById(R.id.tv_title);
        markdownTextView = findViewById(R.id.markdown_text);
        scrollView = findViewById(R.id.scroll_view);
        progressLoading = findViewById(R.id.progress_loading);

        int savedFontSize = readerPrefs.getInt("font_size", Constants.FONT_SIZE_DEFAULT);
        markdownTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedFontSize);

        int savedThemeMode = readerPrefs.getInt("theme_mode", 0);
        float savedLineSpacing = readerPrefs.getFloat("line_spacing", Constants.LINE_SPACING_DEFAULT);
        applyReaderTheme(savedThemeMode, savedLineSpacing);

        findViewById(R.id.btn_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.btn_search).setOnClickListener(v -> showSearchBar());
        findViewById(R.id.btn_toc).setOnClickListener(v -> showTocDialog());
        findViewById(R.id.btn_settings).setOnClickListener(v -> showReaderConfigDialog());

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
            @Override public void afterTextChanged(Editable s) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> performSearch();
                searchHandler.postDelayed(searchRunnable, Constants.SEARCH_DEBOUNCE_MS);
            }
        });

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

    // ---- Markwon & Theme ----

    private void buildMarkwonInstance(int themeMode) {
        boolean isDarkMode = isDarkMode();
        int codeBg = ContextCompat.getColor(this, getThemeColorRes(themeMode, "theme_XXX_code_bg"));
        int codeBlockBg = ContextCompat.getColor(this, getThemeColorRes(themeMode, "theme_XXX_code_block_bg"));
        int blockQuoteColor = ContextCompat.getColor(this, getThemeColorRes(themeMode, "theme_XXX_block_quote"));
        int linkColor;
        if (themeMode == 0) {
            linkColor = ContextCompat.getColor(this, isDarkMode ? R.color.theme_classic_link_dark : R.color.theme_classic_link_light);
        } else {
            linkColor = ContextCompat.getColor(this, getThemeColorRes(themeMode, "theme_XXX_link"));
        }

        markwon = Markwon.builder(this)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(GlideImagesPlugin.create(this))
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(JLatexMathPlugin.create(markdownTextView.getTextSize(), builder -> {
                    builder.inlinesEnabled(true);
                }))
                .usePlugin(TablePlugin.create(this))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.codeBackgroundColor(codeBg);
                        builder.codeBlockBackgroundColor(codeBlockBg);
                        builder.blockQuoteWidth(12);
                        builder.blockQuoteColor(blockQuoteColor);
                        builder.linkColor(linkColor);
                    }
                })
                .build();
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private int getThemeColorRes(int themeMode, String template) {
        String prefix;
        switch (themeMode) {
            case 1: prefix = "theme_sepia"; break;
            case 2: prefix = "theme_green"; break;
            case 3: prefix = "theme_space"; break;
            default: prefix = "theme_classic"; break;
        }
        String suffix = template.substring("theme_XXX".length());
        String resName = prefix + suffix;
        return getResources().getIdentifier(resName, "color", getPackageName());
    }

    private int color(int... colorResIds) {
        for (int id : colorResIds) {
            if (id != 0) return ContextCompat.getColor(this, id);
        }
        return 0;
    }

    private void applyReaderTheme(int themeMode, float lineSpacing) {
        boolean isDarkMode = isDarkMode();

        int bgColor, cardColor, textColor, toolbarBgColor, hintColor;
        boolean lightStatusBar;

        switch (themeMode) {
            case 1: // Sepia
                bgColor = ContextCompat.getColor(this, R.color.theme_sepia_bg);
                cardColor = ContextCompat.getColor(this, R.color.theme_sepia_card);
                textColor = ContextCompat.getColor(this, R.color.theme_sepia_text);
                toolbarBgColor = ContextCompat.getColor(this, R.color.theme_sepia_toolbar);
                hintColor = ContextCompat.getColor(this, R.color.theme_sepia_hint);
                lightStatusBar = true;
                break;
            case 2: // Dark Green
                bgColor = ContextCompat.getColor(this, R.color.theme_green_bg);
                cardColor = ContextCompat.getColor(this, R.color.theme_green_card);
                textColor = ContextCompat.getColor(this, R.color.theme_green_text);
                toolbarBgColor = ContextCompat.getColor(this, R.color.theme_green_toolbar);
                hintColor = ContextCompat.getColor(this, R.color.theme_green_hint);
                lightStatusBar = false;
                break;
            case 3: // Deep Space
                bgColor = ContextCompat.getColor(this, R.color.theme_space_bg);
                cardColor = ContextCompat.getColor(this, R.color.theme_space_card);
                textColor = ContextCompat.getColor(this, R.color.theme_space_text);
                toolbarBgColor = ContextCompat.getColor(this, R.color.theme_space_toolbar);
                hintColor = ContextCompat.getColor(this, R.color.theme_space_hint);
                lightStatusBar = false;
                break;
            default: // Classic (adaptive)
                bgColor = ContextCompat.getColor(this, R.color.ios_background);
                cardColor = ContextCompat.getColor(this, R.color.ios_card_bg);
                textColor = ContextCompat.getColor(this, R.color.ios_text_primary);
                toolbarBgColor = ContextCompat.getColor(this,
                        isDarkMode ? R.color.theme_classic_toolbar_dark : R.color.theme_classic_toolbar_light);
                hintColor = ContextCompat.getColor(this, R.color.ios_text_secondary);
                lightStatusBar = !isDarkMode;
                break;
        }

        applyColorsToViews(bgColor, cardColor, textColor, toolbarBgColor, hintColor);
        markdownTextView.setLineSpacing(0, lineSpacing);

        buildMarkwonInstance(themeMode);
        if (rawMarkdownContent != null && !rawMarkdownContent.isEmpty()) {
            final Spanned spanned = markwon.toMarkdown(rawMarkdownContent);
            markwon.setParsedMarkdown(markdownTextView, spanned);
        }

        if (lightStatusBar) {
            SystemBarUtils.applyLightSystemBars(getWindow());
        } else {
            SystemBarUtils.applyDarkSystemBars(getWindow());
        }
    }

    private void applyColorsToViews(int bgColor, int cardColor, int textColor, int toolbarBgColor, int hintColor) {
        scrollView.setBackgroundColor(bgColor);
        ViewParent parent = scrollView.getParent();
        if (parent instanceof View) {
            ((View) parent).setBackgroundColor(bgColor);
        }

        androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) markdownTextView.getParent();
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
        android.widget.ImageView btnBack = findViewById(R.id.btn_back);
        android.widget.ImageView btnToc = findViewById(R.id.btn_toc);
        android.widget.ImageView btnSearch = findViewById(R.id.btn_search);
        android.widget.ImageView btnSettings = findViewById(R.id.btn_settings);
        if (btnBack != null) btnBack.setColorFilter(iconTint);
        if (btnToc != null) btnToc.setColorFilter(iconTint);
        if (btnSearch != null) btnSearch.setColorFilter(iconTint);
        if (btnSettings != null) btnSettings.setColorFilter(iconTint);

        View searchBarView = findViewById(R.id.search_bar);
        if (searchBarView != null) {
            searchBarView.setBackgroundColor(toolbarBgColor);
        }
        android.widget.EditText etSearchField = findViewById(R.id.et_search);
        if (etSearchField != null) {
            etSearchField.setTextColor(textColor);
            etSearchField.setHintTextColor(hintColor);
        }
        android.widget.ImageView btnSearchPrevView = findViewById(R.id.btn_search_prev);
        android.widget.ImageView btnSearchNextView = findViewById(R.id.btn_search_next);
        android.widget.ImageView btnSearchCloseView = findViewById(R.id.btn_search_close);
        if (btnSearchPrevView != null) btnSearchPrevView.setColorFilter(iconTint);
        if (btnSearchNextView != null) btnSearchNextView.setColorFilter(iconTint);
        if (btnSearchCloseView != null) btnSearchCloseView.setColorFilter(iconTint);
    }

    // ---- Reader Config Dialog ----

    private void showReaderConfigDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reader_config, null);

        View btnDecrease = dialogView.findViewById(R.id.btn_size_decrease);
        View btnIncrease = dialogView.findViewById(R.id.btn_size_increase);
        TextView tvCurrentSize = dialogView.findViewById(R.id.tv_current_size);

        final int[] currentSize = { readerPrefs.getInt("font_size", Constants.FONT_SIZE_DEFAULT) };
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
        if (tocEntries.isEmpty()) {
            Toast.makeText(this, R.string.toc_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_toc, null);
        ListView listView = dialogView.findViewById(R.id.list_toc);
        TocAdapter adapter = new TocAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            TocEntry entry = tocEntries.get(position);
            scrollToLine(entry.lineIndex);
            dialog.dismiss();
        });
        dialog.setContentView(dialogView);
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

        int scrollViewHeight = scrollView.getHeight();
        int targetY = y + markdownTextView.getTop() - (scrollViewHeight / 3);
        if (targetY < 0) targetY = 0;

        scrollView.smoothScrollTo(0, targetY);
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

            float scale = getResources().getDisplayMetrics().density;
            int paddingLeftPx = (int) (((entry.level - 1) * 16 + 8) * scale + 0.5f);
            int paddingTopBottomPx = (int) (12 * scale + 0.5f);
            int paddingRightPx = (int) (8 * scale + 0.5f);
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

    // ---- Search ----

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

            int scrollViewHeight = scrollView.getHeight();
            int targetY = y + markdownTextView.getTop() - (scrollViewHeight / 3);
            if (targetY < 0) targetY = 0;

            scrollView.smoothScrollTo(0, targetY);
        }
    }

    // ---- File Loading ----

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

        currentFileUri = Uri.fromFile(file);
        savedScrollY = RecentFilesManager.getScrollY(this, currentFileUri);

        showLoadingState();

        AppExecutor.getInstance().diskIO().execute(() -> {
            StringBuilder content = new StringBuilder();
            boolean readSuccess = true;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to read file: " + filePath, e);
                readSuccess = false;
            }

            if (!readSuccess) {
                showLoadingError();
                return;
            }

            final String markdown = content.toString();
            extractToc(markdown);
            final Spanned spanned = markwon.toMarkdown(markdown);

            AppExecutor.getInstance().mainThread().post(() -> {
                if (isFinishing() || isDestroyed()) return;
                renderContent(markdown, spanned, currentFileUri);
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
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line).append("\n");
                        }
                        success = true;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to read URI: " + uri, e);
                success = false;
            }

            if (!success) {
                showLoadingError();
                return;
            }

            final String markdown = content.toString();
            extractToc(markdown);
            final Spanned spanned = markwon.toMarkdown(markdown);

            AppExecutor.getInstance().mainThread().post(() -> {
                if (isFinishing() || isDestroyed()) return;
                renderContent(markdown, spanned, uri);
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

    private void renderContent(String markdown, Spanned spanned, Uri uri) {
        rawMarkdownContent = markdown;
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
