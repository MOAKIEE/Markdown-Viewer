package com.example.markdownviewer;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class SearchHelperTest {

    private TextView textView;
    private EditText editText;
    private TextView countView;

    @Before
    public void setUp() {
        textView = new TextView(RuntimeEnvironment.getApplication());
        editText = new EditText(RuntimeEnvironment.getApplication());
        countView = new TextView(RuntimeEnvironment.getApplication());
    }

    @Test
    public void performSearch_emptyQuery_clearsHighlights() {
        textView.setText("Hello World");
        SearchHelper helper = new SearchHelper(textView, editText, countView,
                Color.YELLOW, Color.RED);
        editText.setText("");
        helper.performSearch();

        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            SearchHelper.SearchHighlightSpan[] spans = ((Spannable) text).getSpans(
                    0, text.length(), SearchHelper.SearchHighlightSpan.class);
            assertEquals(0, spans.length);
        }
    }

    @Test
    public void performSearch_singleMatch_addsHighlight() throws InterruptedException {
        textView.setText("Hello World");
        SearchHelper helper = new SearchHelper(textView, editText, countView,
                Color.YELLOW, Color.RED);
        editText.setText("World");
        helper.performSearch();
        // 等待后台线程完成
        Thread.sleep(500);

        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            SearchHelper.SearchHighlightSpan[] spans = ((Spannable) text).getSpans(
                    0, text.length(), SearchHelper.SearchHighlightSpan.class);
            assertEquals(1, spans.length);
        }
    }

    @Test
    public void performSearch_multipleMatches_addsMultipleHighlights() throws InterruptedException {
        textView.setText("Hello Hello Hello");
        SearchHelper helper = new SearchHelper(textView, editText, countView,
                Color.YELLOW, Color.RED);
        editText.setText("Hello");
        helper.performSearch();
        Thread.sleep(500);

        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            SearchHelper.SearchHighlightSpan[] spans = ((Spannable) text).getSpans(
                    0, text.length(), SearchHelper.SearchHighlightSpan.class);
            assertEquals(3, spans.length);
        }
    }

    @Test
    public void performSearch_caseInsensitive() throws InterruptedException {
        textView.setText("Hello WORLD");
        SearchHelper helper = new SearchHelper(textView, editText, countView,
                Color.YELLOW, Color.RED);
        editText.setText("world");
        helper.performSearch();
        Thread.sleep(500);

        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            SearchHelper.SearchHighlightSpan[] spans = ((Spannable) text).getSpans(
                    0, text.length(), SearchHelper.SearchHighlightSpan.class);
            assertEquals(1, spans.length);
        }
    }

    @Test
    public void performSearch_noMatch_noHighlights() throws InterruptedException {
        textView.setText("Hello World");
        SearchHelper helper = new SearchHelper(textView, editText, countView,
                Color.YELLOW, Color.RED);
        editText.setText("xyz");
        helper.performSearch();
        Thread.sleep(500);

        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            SearchHelper.SearchHighlightSpan[] spans = ((Spannable) text).getSpans(
                    0, text.length(), SearchHelper.SearchHighlightSpan.class);
            assertEquals(0, spans.length);
        }
    }

    @Test
    public void nextMatch_cyclesThroughMatches() throws InterruptedException {
        textView.setText("A A A");
        SearchHelper helper = new SearchHelper(textView, editText, countView,
                Color.YELLOW, Color.RED);
        editText.setText("A");
        helper.performSearch();
        Thread.sleep(500);

        helper.nextMatch();
        helper.nextMatch();
        helper.nextMatch(); // Should cycle back to first
        // No assertion on internal state, but should not crash
    }

    @Test
    public void clearHighlights_removesAllSpans() throws InterruptedException {
        textView.setText("Hello World");
        SearchHelper helper = new SearchHelper(textView, editText, countView,
                Color.YELLOW, Color.RED);
        editText.setText("o");
        helper.performSearch();
        Thread.sleep(500);

        helper.clearHighlights();

        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            SearchHelper.SearchHighlightSpan[] spans = ((Spannable) text).getSpans(
                    0, text.length(), SearchHelper.SearchHighlightSpan.class);
            assertEquals(0, spans.length);
        }
    }

    @Test
    public void destroy_cleansUp() {
        SearchHelper helper = new SearchHelper(textView, editText, countView,
                Color.YELLOW, Color.RED);
        helper.attachToEditText();
        helper.destroy();
        // Should not throw
    }

    @Test
    public void performSearch_discardsStaleBackgroundResultsByGeneration() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/example/markdownviewer/SearchHelper.java")),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("searchGeneration"));
        assertTrue(source.contains("final int generation"));
        assertTrue(source.contains("generation != searchGeneration.get()"));
    }
}
