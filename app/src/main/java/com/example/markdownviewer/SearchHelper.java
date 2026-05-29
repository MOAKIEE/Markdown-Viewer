package com.example.markdownviewer;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchHelper {

    private static final int MAX_MATCHES = 500;
    private static final int MAX_QUERY_LENGTH = 200;

    private final WeakReference<TextView> textViewRef;
    private final WeakReference<EditText> etSearchRef;
    private final WeakReference<TextView> tvCountRef;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<int[]> matches = new ArrayList<>();
    private volatile int currentMatch = -1;
    private Runnable pendingSearch;
    private int highlightColor;
    private int currentHighlightColor;
    private final AtomicBoolean isSearching = new AtomicBoolean(false);

    private final TextWatcher textWatcher;

    static final class SearchHighlightSpan extends BackgroundColorSpan {
        SearchHighlightSpan(int color) { super(color); }
    }

    public SearchHelper(TextView textView, EditText etSearch, TextView tvCount,
                        int highlightColor, int currentHighlightColor) {
        this.textViewRef = new WeakReference<>(textView);
        this.etSearchRef = new WeakReference<>(etSearch);
        this.tvCountRef = new WeakReference<>(tvCount);
        this.highlightColor = highlightColor;
        this.currentHighlightColor = currentHighlightColor;
        this.textWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = SearchHelper.this::performSearch;
                handler.postDelayed(pendingSearch, Constants.SEARCH_DEBOUNCE_MS);
            }
        };
    }

    public void attachToEditText() {
        EditText etSearch = etSearchRef.get();
        if (etSearch != null) {
            etSearch.addTextChangedListener(textWatcher);
        }
    }

    public void performSearch() {
        EditText etSearch = etSearchRef.get();
        if (etSearch == null) return;

        String query = etSearch.getText().toString();
        if (query.isEmpty()) {
            clearHighlights();
            return;
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }

        TextView textView = textViewRef.get();
        if (textView == null) return;

        CharSequence text = textView.getText();
        if (text == null) return;

        // 取消之前的搜索
        isSearching.set(true);
        final String finalQuery = query;
        final String src = text.toString();

        // 在后台线程执行搜索
        AppExecutor.getInstance().diskIO().execute(() -> {
            if (!isSearching.get()) return;

            List<int[]> resultMatches = new ArrayList<>();
            try {
                Pattern pattern = Pattern.compile(Pattern.quote(finalQuery), Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(src);
                while (matcher.find() && resultMatches.size() < MAX_MATCHES) {
                    resultMatches.add(new int[]{matcher.start(), matcher.end()});
                }
            } catch (Exception e) {
                String srcLower = src.toLowerCase(Locale.ROOT);
                String queryLower = finalQuery.toLowerCase(Locale.ROOT);
                int queryLen = queryLower.length();
                int index = srcLower.indexOf(queryLower);
                while (index >= 0 && resultMatches.size() < MAX_MATCHES) {
                    resultMatches.add(new int[]{index, index + queryLen});
                    index = srcLower.indexOf(queryLower, index + queryLen);
                }
            }

            final List<int[]> finalMatches = resultMatches;
            AppExecutor.getInstance().mainThread().post(() -> {
                if (!isSearching.get()) return;
                TextView tv = textViewRef.get();
                if (tv == null) return;

                matches.clear();
                matches.addAll(finalMatches);
                currentMatch = matches.isEmpty() ? -1 : 0;

                if (matches.isEmpty()) {
                    clearHighlightSpans();
                    updateCount();
                    return;
                }

                applyHighlights();
                updateCount();
            });
        });
    }

    public void nextMatch() {
        if (matches.isEmpty()) { performSearch(); return; }
        int oldMatch = currentMatch;
        currentMatch = (currentMatch + 1) % matches.size();
        updateCurrentHighlight(oldMatch, currentMatch);
        updateCount();
    }

    public void prevMatch() {
        if (matches.isEmpty()) { performSearch(); return; }
        int oldMatch = currentMatch;
        currentMatch = (currentMatch - 1 + matches.size()) % matches.size();
        updateCurrentHighlight(oldMatch, currentMatch);
        updateCount();
    }

    public void clearHighlights() {
        clearHighlightSpans();
        matches.clear();
        currentMatch = -1;
        TextView tvCount = tvCountRef.get();
        if (tvCount != null) tvCount.setText("");
    }

    public void setColors(int highlightColor, int currentHighlightColor) {
        this.highlightColor = highlightColor;
        this.currentHighlightColor = currentHighlightColor;
    }

    public void destroy() {
        isSearching.set(false);
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        EditText etSearch = etSearchRef.get();
        if (etSearch != null) {
            etSearch.removeTextChangedListener(textWatcher);
        }
        clearHighlights();
    }

    private void clearHighlightSpans() {
        TextView textView = textViewRef.get();
        if (textView == null) return;
        CharSequence text = textView.getText();
        if (!(text instanceof Spannable)) return;
        Spannable spannable = (Spannable) text;
        SearchHighlightSpan[] spans = spannable.getSpans(0, spannable.length(), SearchHighlightSpan.class);
        for (SearchHighlightSpan span : spans) {
            spannable.removeSpan(span);
        }
    }

    private void applyHighlights() {
        TextView textView = textViewRef.get();
        if (textView == null) return;
        CharSequence text = textView.getText();
        if (!(text instanceof Spannable)) return;
        Spannable spannable = (Spannable) text;

        SearchHighlightSpan[] existing = spannable.getSpans(0, spannable.length(), SearchHighlightSpan.class);
        for (SearchHighlightSpan span : existing) {
            spannable.removeSpan(span);
        }

        for (int i = 0; i < matches.size(); i++) {
            int[] match = matches.get(i);
            int color = (i == currentMatch) ? currentHighlightColor : highlightColor;
            spannable.setSpan(new SearchHighlightSpan(color), match[0], match[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void updateCurrentHighlight(int oldIndex, int newIndex) {
        TextView textView = textViewRef.get();
        if (textView == null) return;
        CharSequence text = textView.getText();
        if (!(text instanceof Spannable)) return;
        Spannable spannable = (Spannable) text;

        if (oldIndex >= 0 && oldIndex < matches.size()) {
            int[] oldMatch = matches.get(oldIndex);
            SearchHighlightSpan[] spans = spannable.getSpans(oldMatch[0], oldMatch[1], SearchHighlightSpan.class);
            for (SearchHighlightSpan span : spans) {
                spannable.removeSpan(span);
            }
            spannable.setSpan(new SearchHighlightSpan(highlightColor), oldMatch[0], oldMatch[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (newIndex >= 0 && newIndex < matches.size()) {
            int[] newMatch = matches.get(newIndex);
            SearchHighlightSpan[] spans = spannable.getSpans(newMatch[0], newMatch[1], SearchHighlightSpan.class);
            for (SearchHighlightSpan span : spans) {
                spannable.removeSpan(span);
            }
            spannable.setSpan(new SearchHighlightSpan(currentHighlightColor), newMatch[0], newMatch[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void updateCount() {
        TextView tvCount = tvCountRef.get();
        if (tvCount == null) return;
        if (matches.isEmpty()) {
            tvCount.setText(R.string.search_count_empty);
        } else {
            tvCount.setText(tvCount.getContext().getString(
                    R.string.search_count_format, currentMatch + 1, matches.size()));
        }
    }
}
