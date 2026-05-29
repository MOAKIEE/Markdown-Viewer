package com.example.markdownviewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.noties.markwon.Markwon;

public class MarkdownActivity extends AppCompatActivity {

    private static final String TAG = "MarkdownActivity";
    private static final String STATE_SEARCH_VISIBLE = "search_visible";
    private static final String STATE_SEARCH_TEXT = "search_text";
    private static final String STATE_SCROLL_Y = "scroll_y_instance";

    private TextView markdownTextView;
    private TextView tvTitle;
    private Markwon markwon;
    private ScrollView scrollView;
    private ProgressBar progressLoading;

    private View searchBar;
    private EditText etSearch;

    private SearchHelper searchHelper;

    private int savedScrollY = 0;

    private SharedPreferences readerPrefs;
    private int currentThemeMode = 0;
    private int cachedMarkwonTheme = -1;
    private boolean cachedMarkwonLatex = false;
    private MarkdownViewModel viewModel;
    private final AtomicBoolean loadCancelled = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown);

        // 1. Intent 安全校验（必须在任何逻辑之前）
        if (!validateIntent()) {
            Toast.makeText(this, R.string.error_unsupported_source, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        SystemBarUtils.applyLightSystemBars(getWindow());

        readerPrefs = getSharedPreferences(Constants.PREFS_READER_CONFIG, MODE_PRIVATE);
        currentThemeMode = readerPrefs.getInt(Constants.KEY_THEME_MODE, ReaderTheme.MODE_CLASSIC);

        tvTitle = findViewById(R.id.tv_title);
        markdownTextView = findViewById(R.id.markdown_text);
        scrollView = findViewById(R.id.scroll_view);
        progressLoading = findViewById(R.id.progress_loading);

        int savedFontSize = readerPrefs.getInt(Constants.KEY_FONT_SIZE, Constants.FONT_SIZE_DEFAULT);
        markdownTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedFontSize);

        float savedLineSpacing = readerPrefs.getFloat(Constants.KEY_LINE_SPACING, Constants.LINE_SPACING_DEFAULT);
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

        // 注册返回键处理：搜索栏可见时先关闭搜索栏
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchBar != null && searchBar.getVisibility() == View.VISIBLE) {
                    hideSearchBar();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        viewModel = new ViewModelProvider(this).get(MarkdownViewModel.class);
        observeViewModel();

        Uri fileUri = getIntent().getData();
        String fileName = sanitizeFileName(getIntent().getStringExtra("file_name"));

        if (viewModel.hasContent()) {
            restoreFromViewModel();
        } else {
            if (fileName != null) {
                tvTitle.setText(fileName);
            } else if (fileUri != null) {
                tvTitle.setText(FileUtils.getDisplayName(this, fileUri));
            }
            if (fileUri != null) {
                loadMarkdownFromUri(fileUri);
            }
        }

        // 恢复实例状态
        if (savedInstanceState != null) {
            savedScrollY = savedInstanceState.getInt(STATE_SCROLL_Y, 0);
            boolean searchVisible = savedInstanceState.getBoolean(STATE_SEARCH_VISIBLE, false);
            String searchText = savedInstanceState.getString(STATE_SEARCH_TEXT, "");
            if (searchVisible && searchBar != null && etSearch != null) {
                searchBar.setVisibility(View.VISIBLE);
                etSearch.setText(searchText);
                if (!searchText.isEmpty()) {
                    searchHelper.performSearch();
                }
            }
            if (savedScrollY > 0 && scrollView != null) {
                scrollView.post(() -> scrollView.scrollTo(0, savedScrollY));
            }
        }
    }

    private boolean validateIntent() {
        Intent intent = getIntent();
        if (intent == null) return false;
        Uri data = intent.getData();
        if (data == null) return false;
        return "content".equals(data.getScheme());
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) return null;
        // 限制长度，移除控制字符
        String sanitized = fileName.trim();
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
        }
        return sanitized.replaceAll("[\\x00-\\x1F\\x7F]", "");
    }

    private void observeViewModel() {
        viewModel.getRenderedContent().observe(this, spanned -> {
            if (spanned != null && markwon != null && markdownTextView != null) {
                markwon.setParsedMarkdown(markdownTextView, spanned);
            }
        });

        viewModel.getTitle().observe(this, title -> {
            if (title != null && tvTitle != null) {
                tvTitle.setText(title);
            }
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                if (isLoading) {
                    showLoadingState();
                }
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showLoadingError(error);
            }
        });
    }

    private void restoreFromViewModel() {
        String title = viewModel.getTitle().getValue();
        if (title != null && tvTitle != null) tvTitle.setText(title);

        Integer scroll = viewModel.getScrollY().getValue();
        if (scroll != null && scroll > 0) {
            savedScrollY = scroll;
        }

        Spanned cached = viewModel.getRenderedContent().getValue();
        String raw = viewModel.getRawMarkdownContent().getValue();
        if (raw != null && !raw.isEmpty()) {
            boolean needsLatex = MarkwonFactory.contentNeedsLatex(raw);
            markwon = MarkwonFactory.create(this, markdownTextView, currentThemeMode, needsLatex);
            cachedMarkwonTheme = currentThemeMode;
            cachedMarkwonLatex = needsLatex;
            if (cached != null) {
                markwon.setParsedMarkdown(markdownTextView, cached);
            } else {
                Spanned spanned = markwon.toMarkdown(raw);
                markwon.setParsedMarkdown(markdownTextView, spanned);
                viewModel.setRenderedContent(spanned);
            }
            if (savedScrollY > 0 && scrollView != null) {
                scrollView.post(() -> scrollView.scrollTo(0, savedScrollY));
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Uri currentUri = viewModel.getFileUri().getValue();
        if (scrollView != null && currentUri != null) {
            RecentFilesManager.updateScrollY(this, currentUri, scrollView.getScrollY());
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 只保存轻量 UI 状态，内容数据由 ViewModel 自动保留
        if (searchBar != null) {
            outState.putBoolean(STATE_SEARCH_VISIBLE, searchBar.getVisibility() == View.VISIBLE);
        }
        if (etSearch != null) {
            outState.putString(STATE_SEARCH_TEXT, etSearch.getText().toString());
        }
        if (scrollView != null) {
            outState.putInt(STATE_SCROLL_Y, scrollView.getScrollY());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loadCancelled.set(true);
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
        markdownTextView.setLinkTextColor(ReaderTheme.getLinkColor(this, themeMode));

        String rawContent = viewModel.getRawMarkdownContent().getValue();
        if (rawContent != null && !rawContent.isEmpty()) {
            boolean needsLatex = MarkwonFactory.contentNeedsLatex(rawContent);
            boolean needsRebuild = (themeMode != cachedMarkwonTheme) || (needsLatex != cachedMarkwonLatex);
            if (needsRebuild) {
                cachedMarkwonTheme = themeMode;
                cachedMarkwonLatex = needsLatex;
                markwon = MarkwonFactory.create(this, markdownTextView, themeMode, needsLatex);
            }
            AppExecutor.getInstance().computation().execute(() -> {
                final Spanned spanned = markwon.toMarkdown(rawContent);
                AppExecutor.getInstance().mainThread().post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    markwon.setParsedMarkdown(markdownTextView, spanned);
                    viewModel.setRenderedContent(spanned);
                });
            });
        } else {
            markwon = MarkwonFactory.create(this, markdownTextView, themeMode);
            cachedMarkwonTheme = themeMode;
            cachedMarkwonLatex = false;
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

        // 主题切换后如果搜索栏可见，刷新高亮颜色
        if (searchBar != null && searchBar.getVisibility() == View.VISIBLE && etSearch != null) {
            String query = etSearch.getText().toString();
            if (!query.isEmpty()) {
                searchHelper.performSearch();
            }
        }
    }

    private void applyColorsToViews(int bgColor, int cardColor, int textColor, int toolbarBgColor, int hintColor) {
        if (scrollView == null) return;
        scrollView.setBackgroundColor(bgColor);
        ViewParent parent = scrollView.getParent();
        if (parent instanceof View) {
            ((View) parent).setBackgroundColor(bgColor);
        }

        ViewParent cardParent = markdownTextView.getParent();
        if (cardParent instanceof androidx.cardview.widget.CardView) {
            ((androidx.cardview.widget.CardView) cardParent).setCardBackgroundColor(cardColor);
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

        if (searchBar != null) {
            searchBar.setBackgroundColor(toolbarBgColor);
        }
        if (etSearch != null) {
            etSearch.setTextColor(textColor);
            etSearch.setHintTextColor(hintColor);
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

        final int[] currentSize = {readerPrefs.getInt(Constants.KEY_FONT_SIZE, Constants.FONT_SIZE_DEFAULT)};
        tvCurrentSize.setText(getString(R.string.reader_font_size_default_format, currentSize[0]));

        btnDecrease.setOnClickListener(v -> {
            if (currentSize[0] > Constants.FONT_SIZE_MIN) {
                currentSize[0]--;
                tvCurrentSize.setText(getString(R.string.reader_font_size_default_format, currentSize[0]));
                markdownTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSize[0]);
                readerPrefs.edit().putInt(Constants.KEY_FONT_SIZE, currentSize[0]).apply();
            } else {
                Toast.makeText(this, R.string.error_font_size_min, Toast.LENGTH_SHORT).show();
            }
        });

        btnIncrease.setOnClickListener(v -> {
            if (currentSize[0] < Constants.FONT_SIZE_MAX) {
                currentSize[0]++;
                tvCurrentSize.setText(getString(R.string.reader_font_size_default_format, currentSize[0]));
                markdownTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSize[0]);
                readerPrefs.edit().putInt(Constants.KEY_FONT_SIZE, currentSize[0]).apply();
            } else {
                Toast.makeText(this, R.string.error_font_size_max, Toast.LENGTH_SHORT).show();
            }
        });

        // Line spacing
        View btnSpacing12 = dialogView.findViewById(R.id.btn_spacing_12);
        View btnSpacing14 = dialogView.findViewById(R.id.btn_spacing_14);
        View btnSpacing16 = dialogView.findViewById(R.id.btn_spacing_16);

        Runnable updateSpacingHighlight = () -> {
            float spacing = readerPrefs.getFloat(Constants.KEY_LINE_SPACING, Constants.LINE_SPACING_DEFAULT);
            int activeColor = ContextCompat.getColor(this, R.color.spacing_highlight_active);
            int inactiveColor = ContextCompat.getColor(this, R.color.spacing_highlight_inactive);
            if (btnSpacing12 != null) btnSpacing12.setBackgroundColor(Math.abs(spacing - 1.2f) < 0.05f ? activeColor : inactiveColor);
            if (btnSpacing14 != null) btnSpacing14.setBackgroundColor(Math.abs(spacing - 1.4f) < 0.05f ? activeColor : inactiveColor);
            if (btnSpacing16 != null) btnSpacing16.setBackgroundColor(Math.abs(spacing - 1.6f) < 0.05f ? activeColor : inactiveColor);
        };
        updateSpacingHighlight.run();

        View.OnClickListener spacingListener = v -> {
            float newSpacing;
            if (v.getId() == R.id.btn_spacing_12) newSpacing = 1.2f;
            else if (v.getId() == R.id.btn_spacing_14) newSpacing = 1.4f;
            else newSpacing = 1.6f;
            readerPrefs.edit().putFloat(Constants.KEY_LINE_SPACING, newSpacing).apply();
            updateSpacingHighlight.run();
            applyReaderTheme(readerPrefs.getInt(Constants.KEY_THEME_MODE, ReaderTheme.MODE_CLASSIC), newSpacing);
        };
        if (btnSpacing12 != null) btnSpacing12.setOnClickListener(spacingListener);
        if (btnSpacing14 != null) btnSpacing14.setOnClickListener(spacingListener);
        if (btnSpacing16 != null) btnSpacing16.setOnClickListener(spacingListener);

        // Theme
        View btnClassic = dialogView.findViewById(R.id.theme_classic);
        View btnSepia = dialogView.findViewById(R.id.theme_sepia);
        View btnGreen = dialogView.findViewById(R.id.theme_green);
        View btnSpace = dialogView.findViewById(R.id.theme_space);

        Runnable updateThemeHighlight = () -> {
            int theme = readerPrefs.getInt(Constants.KEY_THEME_MODE, ReaderTheme.MODE_CLASSIC);
            if (btnClassic != null) btnClassic.setForeground(theme == ReaderTheme.MODE_CLASSIC ? ContextCompat.getDrawable(this, R.drawable.glass_card_stroke) : null);
            if (btnSepia != null) btnSepia.setForeground(theme == ReaderTheme.MODE_SEPIA ? ContextCompat.getDrawable(this, R.drawable.glass_card_stroke) : null);
            if (btnGreen != null) btnGreen.setForeground(theme == ReaderTheme.MODE_GREEN ? ContextCompat.getDrawable(this, R.drawable.glass_card_stroke) : null);
            if (btnSpace != null) btnSpace.setForeground(theme == ReaderTheme.MODE_SPACE ? ContextCompat.getDrawable(this, R.drawable.glass_card_stroke) : null);

            float selectedElevation = 8f;
            if (btnClassic != null) btnClassic.setElevation(theme == ReaderTheme.MODE_CLASSIC ? selectedElevation : 0f);
            if (btnSepia != null) btnSepia.setElevation(theme == ReaderTheme.MODE_SEPIA ? selectedElevation : 0f);
            if (btnGreen != null) btnGreen.setElevation(theme == ReaderTheme.MODE_GREEN ? selectedElevation : 0f);
            if (btnSpace != null) btnSpace.setElevation(theme == ReaderTheme.MODE_SPACE ? selectedElevation : 0f);
        };
        updateThemeHighlight.run();

        View.OnClickListener themeListener = v -> {
            int newTheme;
            if (v.getId() == R.id.theme_classic) newTheme = ReaderTheme.MODE_CLASSIC;
            else if (v.getId() == R.id.theme_sepia) newTheme = ReaderTheme.MODE_SEPIA;
            else if (v.getId() == R.id.theme_green) newTheme = ReaderTheme.MODE_GREEN;
            else newTheme = ReaderTheme.MODE_SPACE;
            readerPrefs.edit().putInt(Constants.KEY_THEME_MODE, newTheme).apply();
            currentThemeMode = newTheme;
            updateThemeHighlight.run();
            float spacing = readerPrefs.getFloat(Constants.KEY_LINE_SPACING, Constants.LINE_SPACING_DEFAULT);
            applyReaderTheme(newTheme, spacing);
        };
        if (btnClassic != null) btnClassic.setOnClickListener(themeListener);
        if (btnSepia != null) btnSepia.setOnClickListener(themeListener);
        if (btnGreen != null) btnGreen.setOnClickListener(themeListener);
        if (btnSpace != null) btnSpace.setOnClickListener(themeListener);

        dialog.setContentView(dialogView);
        dialog.show();
    }

    // ---- TOC ----

    private void showTocDialog() {
        List<TocParser.TocEntry> entries = viewModel.getTocEntries().getValue();
        if (entries == null || entries.isEmpty()) {
            Toast.makeText(this, R.string.toc_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_toc, null);
        ListView listView = dialogView.findViewById(R.id.list_toc);
        TocAdapter adapter = new TocAdapter(this, entries);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            TocParser.TocEntry entry = entries.get(position);
            scrollToLine(entry.lineIndex);
            dialog.dismiss();
        });
        dialog.setContentView(dialogView);
        dialog.show();
    }

    private void scrollToLine(int lineIndex) {
        CharSequence text = markdownTextView.getText();
        if (text == null) return;

        List<TocParser.TocEntry> entries = viewModel.getTocEntries().getValue();
        int offset = 0;
        if (entries != null) {
            for (TocParser.TocEntry entry : entries) {
                if (entry.lineIndex == lineIndex) {
                    offset = entry.charOffset;
                    break;
                }
            }
        }

        final int targetOffset = offset;
        markdownTextView.post(() -> {
            android.text.Layout layout = markdownTextView.getLayout();
            if (layout == null) return;
            CharSequence t = markdownTextView.getText();
            if (t == null) return;

            int line = layout.getLineForOffset(Math.min(targetOffset, t.length()));
            int y = layout.getLineTop(line);

            int scrollViewHeight = scrollView.getHeight();
            int targetY = y + markdownTextView.getTop() - (scrollViewHeight / 3);
            if (targetY < 0) targetY = 0;

            scrollView.smoothScrollTo(0, targetY);
        });
    }

    private static class TocAdapter extends BaseAdapter {
        private final float density;
        private final List<TocParser.TocEntry> entries;

        TocAdapter(android.content.Context context, List<TocParser.TocEntry> entries) {
            this.density = context.getResources().getDisplayMetrics().density;
            this.entries = entries;
        }

        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_toc, parent, false);
                holder = new ViewHolder();
                holder.tvTitle = convertView.findViewById(R.id.tv_toc_title);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            TocParser.TocEntry entry = entries.get(position);

            int paddingLeftPx = (int) (((entry.level - 1) * 16 + 8) * density + 0.5f);
            int paddingTopBottomPx = (int) (12 * density + 0.5f);
            int paddingRightPx = (int) (8 * density + 0.5f);
            convertView.setPadding(paddingLeftPx, paddingTopBottomPx, paddingRightPx, paddingTopBottomPx);

            if (entry.level == 1) {
                holder.tvTitle.setTextSize(16.5f);
                holder.tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.tvTitle.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.ios_text_primary));
            } else if (entry.level == 2) {
                holder.tvTitle.setTextSize(14.5f);
                holder.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.tvTitle.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.ios_text_primary));
            } else {
                holder.tvTitle.setTextSize(13.0f);
                holder.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.tvTitle.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.ios_text_secondary));
            }

            holder.tvTitle.setText(entry.title);
            return convertView;
        }
        static class ViewHolder { TextView tvTitle; }
    }

    // ---- Search UI ----

    private void showSearchBar() {
        if (searchBar != null) {
            searchBar.setVisibility(View.VISIBLE);
        }
        if (etSearch != null) {
            etSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void hideSearchBar() {
        if (searchBar != null) {
            searchBar.setVisibility(View.GONE);
        }
        if (searchHelper != null) searchHelper.clearHighlights();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && etSearch != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
    }

    // ---- File Loading ----

    private void loadMarkdownFromUri(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (!"content".equals(scheme)) {
            Toast.makeText(this, R.string.error_unsupported_source, Toast.LENGTH_SHORT).show();
            return;
        }

        loadCancelled.set(false);
        viewModel.setFileUri(uri);
        viewModel.setIsLoading(true);
        viewModel.setErrorMessage(null);
        savedScrollY = RecentFilesManager.getScrollY(this, uri);

        showLoadingState();

        boolean needsLatex = false; // 先不判断，在 Repository 中处理
        markwon = MarkwonFactory.create(this, markdownTextView, currentThemeMode);
        cachedMarkwonTheme = currentThemeMode;
        cachedMarkwonLatex = false;

        MarkdownRepository.loadMarkdownAsync(this, uri, markwon,
                result -> {
                    if (isFinishing() || isDestroyed()) return;
                    viewModel.setIsLoading(false);

                    if (!result.success) {
                        showLoadingError(result.errorMessage);
                        return;
                    }

                    viewModel.setRawMarkdownContent(result.rawMarkdown);
                    viewModel.setRenderedContent(result.renderedContent);
                    viewModel.setTocEntries(result.tocEntries);
                    viewModel.setTitle(result.title);

                    renderContent(result.renderedContent, uri);
                }, loadCancelled);
    }

    private void showLoadingState() {
        if (progressLoading == null || scrollView == null) return;
        progressLoading.setAlpha(1f);
        progressLoading.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);
    }

    private void showLoadingError(String error) {
        if (isFinishing() || isDestroyed()) return;
        if (progressLoading != null) progressLoading.setVisibility(View.GONE);
        if (scrollView != null) {
            scrollView.setAlpha(1f);
            scrollView.setVisibility(View.VISIBLE);
        }
        String message = error != null ? error : getString(R.string.error_read_failed);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void renderContent(Spanned spanned, Uri uri) {
        if (markwon != null && markdownTextView != null) {
            markwon.setParsedMarkdown(markdownTextView, spanned);
        }
        RecentFilesManager.addRecentFile(this, uri);

        if (progressLoading != null) {
            progressLoading.animate().alpha(0f).setDuration(Constants.ANIM_DURATION_FADE).withEndAction(() -> {
                progressLoading.setVisibility(View.GONE);
                progressLoading.setAlpha(1f);
            });
        }
        if (scrollView != null) {
            scrollView.setAlpha(0f);
            scrollView.setVisibility(View.VISIBLE);
            scrollView.animate().alpha(1f).setDuration(Constants.ANIM_DURATION_APPEAR).setListener(null);
        }

        if (savedScrollY > 0 && scrollView != null) {
            scrollView.post(() -> scrollView.scrollTo(0, savedScrollY));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!validateIntent()) {
            Toast.makeText(this, R.string.error_unsupported_source, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri fileUri = intent.getData();
        String fileName = sanitizeFileName(intent.getStringExtra("file_name"));
        if (fileName != null && tvTitle != null) {
            tvTitle.setText(fileName);
        } else if (fileUri != null && tvTitle != null) {
            tvTitle.setText(FileUtils.getDisplayName(this, fileUri));
        }
        if (fileUri != null) {
            // 清除旧内容
            viewModel.clear();
            if (searchHelper != null) searchHelper.clearHighlights();
            loadMarkdownFromUri(fileUri);
        }
    }
}
