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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchHelper {

    private final TextView textView;
    private final EditText etSearch;
    private final TextView tvCount;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<int[]> matches = new ArrayList<>();
    private int currentMatch = -1;
    private Runnable pendingSearch;
    private int highlightColor;
    private int currentHighlightColor;

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
            while (matcher.find()) {
                matches.add(new int[]{matcher.start(), matcher.end()});
            }
        } catch (Exception e) {
            // 回退到 indexOf
            int index = src.toLowerCase().indexOf(query.toLowerCase());
            while (index >= 0) {
                matches.add(new int[]{index, index + query.length()});
                index = src.toLowerCase().indexOf(query.toLowerCase(), index + 1);
            }
        }

        if (matches.isEmpty()) {
            tvCount.setText("0/0");
            clearHighlights();
            return;
        }

        currentMatch = 0;
        applyHighlights();
        updateCount();
    }

    public void nextMatch() {
        if (matches.isEmpty()) { performSearch(); return; }
        currentMatch = (currentMatch + 1) % matches.size();
        applyHighlights();
        updateCount();
    }

    public void prevMatch() {
        if (matches.isEmpty()) { performSearch(); return; }
        currentMatch = (currentMatch - 1 + matches.size()) % matches.size();
        applyHighlights();
        updateCount();
    }

    public void clearHighlights() {
        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            Spannable spannable = (Spannable) text;
            BackgroundColorSpan[] spans = spannable.getSpans(0, spannable.length(), BackgroundColorSpan.class);
            for (BackgroundColorSpan span : spans) {
                spannable.removeSpan(span);
            }
        }
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

    private void applyHighlights() {
        CharSequence text = textView.getText();
        if (!(text instanceof Spannable)) return;
        Spannable spannable = (Spannable) text;

        // 仅移除已存在的高亮 span，避免全量扫描所有 span 类型
        BackgroundColorSpan[] existing = spannable.getSpans(0, spannable.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : existing) {
            spannable.removeSpan(span);
        }

        for (int i = 0; i < matches.size(); i++) {
            int[] match = matches.get(i);
            int color = (i == currentMatch) ? currentHighlightColor : highlightColor;
            spannable.setSpan(new BackgroundColorSpan(color), match[0], match[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void updateCount() {
        if (matches.isEmpty()) {
            tvCount.setText("0/0");
        } else {
            tvCount.setText((currentMatch + 1) + "/" + matches.size());
        }
    }
}
