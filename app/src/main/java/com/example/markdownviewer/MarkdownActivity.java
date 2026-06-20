package com.example.markdownviewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.markdownviewer.databinding.ActivityMarkdownBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.noties.markwon.Markwon;

public class MarkdownActivity extends AppCompatActivity {

    private static final String TAG = "MarkdownActivity";
    private static final String STATE_SEARCH_VISIBLE = "search_visible";
    private static final String STATE_SEARCH_TEXT = "search_text";
    private static final String STATE_SCROLL_Y = "scroll_y_instance";

    private ActivityMarkdownBinding binding;
    private Markwon markwon;

    private SearchHelper searchHelper;

    private int savedScrollY = 0;

    private SharedPreferences readerPrefs;
    private int currentThemeMode = 0;
    private int cachedMarkwonTheme = -1;
    private boolean cachedMarkwonLatex = false;
    private MarkdownViewModel viewModel;
    private final AtomicBoolean loadCancelled = new AtomicBoolean(false);
    private int loadGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMarkdownBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. Intent 安全校验（必须在任何逻辑之前）
        if (!validateIntent()) {
            Toast.makeText(this, R.string.error_unsupported_source, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        SystemBarUtils.applyLightSystemBars(getWindow());

        readerPrefs = getSharedPreferences(Constants.PREFS_READER_CONFIG, MODE_PRIVATE);
        currentThemeMode = readerPrefs.getInt(Constants.KEY_THEME_MODE, ReaderTheme.MODE_CLASSIC);
        viewModel = new ViewModelProvider(this).get(MarkdownViewModel.class);
        observeViewModel();

        int savedFontSize = readerPrefs.getInt(Constants.KEY_FONT_SIZE, Constants.FONT_SIZE_DEFAULT);
        binding.markdownText.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedFontSize);

        float savedLineSpacing = readerPrefs.getFloat(Constants.KEY_LINE_SPACING, Constants.LINE_SPACING_DEFAULT);
        applyReaderTheme(currentThemeMode, savedLineSpacing);

        binding.btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.btnSearch.setOnClickListener(v -> showSearchBar());
        binding.btnToc.setOnClickListener(v -> showTocDialog());
        binding.btnSettings.setOnClickListener(v -> showReaderConfigDialog());

        SystemBarUtils.applyInsetsToView(binding.toolbarContainer, true, false);

        searchHelper = new SearchHelper(binding.markdownText, binding.etSearch, binding.tvSearchCount,
                ReaderTheme.getHighlightColor(this, currentThemeMode),
                ReaderTheme.getCurrentHighlightColor(this, currentThemeMode));
        searchHelper.attachToEditText();

        binding.btnSearchClose.setOnClickListener(v -> hideSearchBar());
        binding.btnSearchNext.setOnClickListener(v -> searchHelper.nextMatch());
        binding.btnSearchPrev.setOnClickListener(v -> searchHelper.prevMatch());

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
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
                if (binding.searchBar.getVisibility() == View.VISIBLE) {
                    hideSearchBar();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        Uri fileUri = getIntent().getData();
        String fileName = sanitizeFileName(getIntent().getStringExtra("file_name"));

        if (viewModel.hasContent()) {
            restoreFromViewModel();
        } else {
            if (fileName != null) {
                binding.tvTitle.setText(fileName);
            } else if (fileUri != null) {
                binding.tvTitle.setText(FileUtils.getDisplayName(this, fileUri));
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
            if (searchVisible) {
                binding.searchBar.setVisibility(View.VISIBLE);
                binding.etSearch.setText(searchText);
                if (!searchText.isEmpty()) {
                    searchHelper.performSearch();
                }
            }
            if (savedScrollY > 0) {
                binding.scrollView.post(() -> binding.scrollView.scrollTo(0, savedScrollY));
            }
        }
    }

    private boolean validateIntent() {
        Intent intent = getIntent();
        if (intent == null) return false;
        Uri data = intent.getData();
        if (data == null) return false;
        if (!"content".equals(data.getScheme())) return false;

        // 外部应用通过 ACTION_VIEW 调起时，校验调用者已安装，防止伪造 content:// 指向恶意 Provider
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            String caller = getCallingPackage();
            if (caller != null && !caller.equals(getPackageName())) {
                try {
                    getPackageManager().getPackageInfo(caller, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Rejected ACTION_VIEW from unknown caller: " + caller);
                    return false;
                }
            }
        }
        return true;
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) return null;
        String sanitized = fileName.trim();
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
        }
        return sanitized.replaceAll("[\\u0000-\\u001F\\u007F]", "");
    }

    private void observeViewModel() {
        viewModel.getRenderedContent().observe(this, spanned -> {
            if (spanned != null && markwon != null) {
                markwon.setParsedMarkdown(binding.markdownText, spanned);
            }
        });

        viewModel.getTitle().observe(this, title -> {
            if (title != null) {
                binding.tvTitle.setText(title);
            }
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null && isLoading) {
                showLoadingState();
            }
        });
    }

    private void restoreFromViewModel() {
        String title = viewModel.getTitle().getValue();
        if (title != null) binding.tvTitle.setText(title);

        Integer scroll = viewModel.getScrollY().getValue();
        if (scroll != null && scroll > 0) {
            savedScrollY = scroll;
        }

        Spanned cached = viewModel.getRenderedContent().getValue();
        String raw = viewModel.getRawMarkdownContent().getValue();
        if (raw != null && !raw.isEmpty()) {
            boolean needsLatex = MarkwonFactory.contentNeedsLatex(raw);
            markwon = MarkwonFactory.create(this, binding.markdownText, currentThemeMode, needsLatex);
            cachedMarkwonTheme = currentThemeMode;
            cachedMarkwonLatex = needsLatex;
            if (cached != null) {
                markwon.setParsedMarkdown(binding.markdownText, cached);
            } else {
                Spanned spanned = markwon.toMarkdown(raw);
                markwon.setParsedMarkdown(binding.markdownText, spanned);
                viewModel.setRenderedContent(spanned);
            }
            if (savedScrollY > 0) {
                binding.scrollView.post(() -> binding.scrollView.scrollTo(0, savedScrollY));
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (binding == null) return;
        Uri currentUri = viewModel.getFileUri().getValue();
        if (currentUri != null) {
            RecentFilesManager.updateScrollY(this, currentUri, binding.scrollView.getScrollY());
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SEARCH_VISIBLE, binding.searchBar.getVisibility() == View.VISIBLE);
        outState.putString(STATE_SEARCH_TEXT, binding.etSearch.getText().toString());
        outState.putInt(STATE_SCROLL_Y, binding.scrollView.getScrollY());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loadCancelled.set(true);
        if (searchHelper != null) searchHelper.destroy();
        binding = null;
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
        binding.markdownText.setLineSpacing(0, lineSpacing);
        binding.markdownText.setLinkTextColor(ReaderTheme.getLinkColor(this, themeMode));

        String rawContent = viewModel.getRawMarkdownContent().getValue();
        if (rawContent != null && !rawContent.isEmpty()) {
            boolean needsLatex = MarkwonFactory.contentNeedsLatex(rawContent);
            boolean needsRebuild = (themeMode != cachedMarkwonTheme) || (needsLatex != cachedMarkwonLatex);
            if (needsRebuild) {
                cachedMarkwonTheme = themeMode;
                cachedMarkwonLatex = needsLatex;
                markwon = MarkwonFactory.create(this, binding.markdownText, themeMode, needsLatex);
            }
            AppExecutor.getInstance().computation().execute(() -> {
                final Spanned spanned = markwon.toMarkdown(rawContent);
                AppExecutor.getInstance().mainThread().post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    markwon.setParsedMarkdown(binding.markdownText, spanned);
                    viewModel.setRenderedContent(spanned);
                });
            });
        } else {
            markwon = MarkwonFactory.create(this, binding.markdownText, themeMode);
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
        if (binding.searchBar.getVisibility() == View.VISIBLE && !binding.etSearch.getText().toString().isEmpty()) {
            searchHelper.performSearch();
        }
    }

    private void applyColorsToViews(int bgColor, int cardColor, int textColor, int toolbarBgColor, int hintColor) {
        binding.scrollView.setBackgroundColor(bgColor);
        ViewParent parent = binding.scrollView.getParent();
        if (parent instanceof View) {
            ((View) parent).setBackgroundColor(bgColor);
        }

        ViewParent cardParent = binding.markdownText.getParent();
        if (cardParent instanceof androidx.cardview.widget.CardView) {
            ((androidx.cardview.widget.CardView) cardParent).setCardBackgroundColor(cardColor);
        }

        binding.markdownText.setTextColor(textColor);
        binding.tvTitle.setTextColor(textColor);
        binding.toolbarContainer.setBackgroundColor(toolbarBgColor);

        int iconTint = textColor;
        binding.btnBack.setColorFilter(iconTint);
        binding.btnToc.setColorFilter(iconTint);
        binding.btnSearch.setColorFilter(iconTint);
        binding.btnSettings.setColorFilter(iconTint);

        binding.searchBar.setBackgroundColor(toolbarBgColor);
        binding.etSearch.setTextColor(textColor);
        binding.etSearch.setHintTextColor(hintColor);
        binding.btnSearchPrev.setColorFilter(iconTint);
        binding.btnSearchNext.setColorFilter(iconTint);
        binding.btnSearchClose.setColorFilter(iconTint);
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
                binding.markdownText.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSize[0]);
                readerPrefs.edit().putInt(Constants.KEY_FONT_SIZE, currentSize[0]).apply();
            } else {
                Toast.makeText(this, R.string.error_font_size_min, Toast.LENGTH_SHORT).show();
            }
        });

        btnIncrease.setOnClickListener(v -> {
            if (currentSize[0] < Constants.FONT_SIZE_MAX) {
                currentSize[0]++;
                tvCurrentSize.setText(getString(R.string.reader_font_size_default_format, currentSize[0]));
                binding.markdownText.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSize[0]);
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
        CharSequence text = binding.markdownText.getText();
        if (text == null) return;

        List<TocParser.TocEntry> entries = viewModel.getTocEntries().getValue();
        final int targetOffset = resolveRenderedHeadingOffset(text, entries, lineIndex);
        binding.markdownText.post(() -> {
            android.text.Layout layout = binding.markdownText.getLayout();
            if (layout == null) return;
            CharSequence t = binding.markdownText.getText();
            if (t == null) return;

            int line = layout.getLineForOffset(Math.min(targetOffset, t.length()));
            int y = layout.getLineTop(line);

            int scrollViewHeight = binding.scrollView.getHeight();
            int targetY = y + binding.markdownText.getTop() - (scrollViewHeight / 3);
            if (targetY < 0) targetY = 0;

            binding.scrollView.smoothScrollTo(0, targetY);
        });
    }

    static int resolveRenderedHeadingOffset(CharSequence renderedText,
                                            List<TocParser.TocEntry> entries,
                                            int lineIndex) {
        if (renderedText == null || renderedText.length() == 0) return 0;
        TocParser.TocEntry target = null;
        if (entries != null) {
            for (TocParser.TocEntry entry : entries) {
                if (entry.lineIndex == lineIndex) {
                    target = entry;
                    break;
                }
            }
        }
        if (target == null) return 0;

        String targetTitle = normalizeRenderedHeadingTitle(target.title);
        if (!targetTitle.isEmpty()) {
            int occurrenceIndex = 0;
            if (entries != null) {
                for (TocParser.TocEntry entry : entries) {
                    if (entry.lineIndex == lineIndex) break;
                    if (targetTitle.equals(normalizeRenderedHeadingTitle(entry.title))) {
                        occurrenceIndex++;
                    }
                }
            }

            String rendered = renderedText.toString();
            int renderedOffset = nthLineStartIndexOf(rendered, targetTitle, occurrenceIndex);
            if (renderedOffset < 0) {
                renderedOffset = nthIndexOf(rendered, targetTitle, occurrenceIndex);
            }
            if (renderedOffset >= 0) return renderedOffset;
        }

        return Math.max(0, Math.min(target.charOffset, renderedText.length()));
    }

    private static String normalizeRenderedHeadingTitle(String title) {
        if (title == null) return "";
        String normalized = title
                .replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
                .replaceAll("<[^>]+>", "")
                .replace("`", "")
                .replace("*", "")
                .replace("_", "")
                .replace("~", "")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private static int nthIndexOf(String text, String query, int occurrenceIndex) {
        int fromIndex = 0;
        int found = -1;
        for (int i = 0; i <= occurrenceIndex; i++) {
            found = text.indexOf(query, fromIndex);
            if (found < 0) return -1;
            fromIndex = found + query.length();
        }
        return found;
    }

    private static int nthLineStartIndexOf(String text, String query, int occurrenceIndex) {
        int fromIndex = 0;
        int seen = 0;
        while (fromIndex <= text.length()) {
            int found = text.indexOf(query, fromIndex);
            if (found < 0) return -1;
            boolean atLineStart = found == 0 || text.charAt(found - 1) == '\n';
            if (atLineStart) {
                if (seen == occurrenceIndex) return found;
                seen++;
            }
            fromIndex = found + query.length();
        }
        return -1;
    }

    // ---- Search UI ----

    private void showSearchBar() {
        binding.searchBar.setVisibility(View.VISIBLE);
        binding.etSearch.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSearchBar() {
        binding.searchBar.setVisibility(View.GONE);
        searchHelper.clearHighlights();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);
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

        final int generation = ++loadGeneration;
        loadCancelled.set(false);
        viewModel.setFileUri(uri);
        viewModel.setIsLoading(true);
        viewModel.setErrorMessage(null);
        savedScrollY = 0;

        showLoadingState();

        markwon = MarkwonFactory.create(this, binding.markdownText, currentThemeMode);
        cachedMarkwonTheme = currentThemeMode;
        cachedMarkwonLatex = false;

        RecentFilesManager.getScrollYAsync(this, uri, scrollY -> {
            if (!loadCancelled.get() && generation == loadGeneration) {
                savedScrollY = scrollY;
                restoreSavedScroll();
            }
        });

        MarkdownRepository.loadMarkdownAsync(this, uri, markwon,
                result -> {
                    if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                    viewModel.setIsLoading(false);

                    if (!result.success) {
                        showLoadingError(result.errorCode);
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
        binding.progressLoading.setAlpha(1f);
        binding.progressLoading.setVisibility(View.VISIBLE);
        binding.scrollView.setVisibility(View.GONE);
    }

    private void showLoadingError(int errorCode) {
        if (isFinishing() || isDestroyed()) return;
        binding.progressLoading.setVisibility(View.GONE);
        binding.scrollView.setAlpha(1f);
        binding.scrollView.setVisibility(View.VISIBLE);
        Toast.makeText(this, messageForError(errorCode), Toast.LENGTH_SHORT).show();
    }

    /** 将 Repository 错误码映射到本地化文案；未知错误码回退到通用读取失败提示。 */
    private String messageForError(int errorCode) {
        switch (errorCode) {
            case MarkdownRepository.ERR_TOO_LARGE:
                return getString(R.string.error_file_too_large);
            case MarkdownRepository.ERR_UNSUPPORTED_SCHEME:
                return getString(R.string.error_unsupported_source);
            case MarkdownRepository.ERR_RENDER_FAILED:
                return getString(R.string.error_render_failed);
            case MarkdownRepository.ERR_READ_FAILED:
            case MarkdownRepository.ERR_CANCELLED:
            case MarkdownRepository.ERR_NONE:
            default:
                return getString(R.string.error_read_failed);
        }
    }

    private void renderContent(Spanned spanned, Uri uri) {
        if (markwon != null) {
            markwon.setParsedMarkdown(binding.markdownText, spanned);
        }
        RecentFilesManager.addRecentFile(this, uri);

        binding.progressLoading.animate().alpha(0f).setDuration(Constants.ANIM_DURATION_FADE).withEndAction(() -> {
            binding.progressLoading.setVisibility(View.GONE);
            binding.progressLoading.setAlpha(1f);
        });
        binding.scrollView.setAlpha(0f);
        binding.scrollView.setVisibility(View.VISIBLE);
        binding.scrollView.animate().alpha(1f).setDuration(Constants.ANIM_DURATION_APPEAR).setListener(null);

        if (savedScrollY > 0) {
            restoreSavedScroll();
        }
    }

    private void restoreSavedScroll() {
        if (savedScrollY <= 0 || binding == null) return;
        binding.scrollView.post(() -> {
            if (isFinishing() || isDestroyed() || binding == null) return;
            binding.scrollView.scrollTo(0, savedScrollY);
        });
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
        if (fileName != null) {
            binding.tvTitle.setText(fileName);
        } else if (fileUri != null) {
            binding.tvTitle.setText(FileUtils.getDisplayName(this, fileUri));
        }
        if (fileUri != null) {
            viewModel.clear();
            if (searchHelper != null) searchHelper.clearHighlights();
            loadMarkdownFromUri(fileUri);
        }
    }
}
