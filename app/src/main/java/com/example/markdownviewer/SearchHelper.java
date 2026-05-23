package com.example.markdownviewer;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchHelper {

    private static final int MAX_MATCHES = 500;

    private final TextView textView;
    private final EditText etSearch;
    private final TextView tvCount;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<int[]> matches = new ArrayList<>();
    private int currentMatch = -1;
    private Runnable pendingSearch;
    private int highlightColor;
    private int currentHighlightColor;

    static final class SearchHighlightSpan extends BackgroundColorSpan {
        SearchHighlightSpan(int color) { super(color); }
    }

    public SearchHelper(TextView textView, EditText etSearch, TextView tvCount,
                        int highlightColor, int currentHighlightColor) {
        this.textView = textView;
        this.etSearch = etSearch;
        this.tvCount = tvCount;
        this.highlightColor = highlightColor;
        this.currentHighlightColor = currentHighlightColor;
    }

    public void attachToEditText() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = SearchHelper.this::performSearch;
                handler.postDelayed(pendingSearch, Constants.SEARCH_DEBOUNCE_MS);
            }
        });
    }

    public void performSearch() {
        String query = etSearch.getText().toString();
        if (query.isEmpty()) {
            clearHighlights();
            return;
        }

        CharSequence text = textView.getText();
        if (text == null) return;
        String src = text.toString();

        matches.clear();
        currentMatch = -1;

        try {
            Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(src);
            while (matcher.find() && matches.size() < MAX_MATCHES) {
                matches.add(new int[]{matcher.start(), matcher.end()});
            }
        } catch (Exception e) {
            String srcLower = src.toLowerCase(Locale.ROOT);
            String queryLower = query.toLowerCase(Locale.ROOT);
            int index = srcLower.indexOf(queryLower);
            while (index >= 0 && matches.size() < MAX_MATCHES) {
                matches.add(new int[]{index, index + query.length()});
                index = srcLower.indexOf(queryLower, index + 1);
            }
        }

        if (matches.isEmpty()) {
            clearHighlightSpans();
            currentMatch = -1;
            tvCount.setText(R.string.search_count_empty);
            return;
        }

        currentMatch = 0;
        applyHighlights();
        updateCount();
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
        tvCount.setText("");
    }

    public void setColors(int highlightColor, int currentHighlightColor) {
        this.highlightColor = highlightColor;
        this.currentHighlightColor = currentHighlightColor;
    }

    public void destroy() {
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
    }

    private void clearHighlightSpans() {
        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            Spannable spannable = (Spannable) text;
            SearchHighlightSpan[] spans = spannable.getSpans(0, spannable.length(), SearchHighlightSpan.class);
            for (SearchHighlightSpan span : spans) {
                spannable.removeSpan(span);
            }
        }
    }

    private void applyHighlights() {
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
        if (matches.isEmpty()) {
            tvCount.setText(R.string.search_count_empty);
        } else {
            tvCount.setText(tvCount.getContext().getString(
                    R.string.search_count_format, currentMatch + 1, matches.size()));
        }
    }
}
