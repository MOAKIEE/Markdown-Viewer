package com.example.markdownviewer;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MarkdownActivityTest {

    @Test
    public void onCreate_initializesViewModelBeforeApplyingReaderTheme() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/example/markdownviewer/MarkdownActivity.java")),
                StandardCharsets.UTF_8);

        Matcher method = Pattern.compile(
                "protected void onCreate\\(Bundle savedInstanceState\\) \\{([\\s\\S]*?)\\n    \\}",
                Pattern.MULTILINE).matcher(source);
        assertTrue(method.find());

        String onCreateBody = method.group(1);
        int initializeViewModel = onCreateBody.indexOf("viewModel = new ViewModelProvider(this).get");
        int applyTheme = onCreateBody.indexOf("applyReaderTheme(currentThemeMode, savedLineSpacing)");

        assertTrue(initializeViewModel >= 0);
        assertTrue(applyTheme >= 0);
        assertTrue(initializeViewModel < applyTheme);
    }

    @Test
    public void resolveRenderedHeadingOffset_usesRenderedHeadingTextInsteadOfRawOffset() {
        List<TocParser.TocEntry> entries = Collections.singletonList(
                new TocParser.TocEntry("Second Heading", 2, 5, 999));

        int offset = MarkdownActivity.resolveRenderedHeadingOffset(
                "Intro\nSecond Heading\nBody", entries, 5);

        assertEquals(6, offset);
    }

    @Test
    public void resolveRenderedHeadingOffset_handlesDuplicateHeadingsByTocOrder() {
        List<TocParser.TocEntry> entries = Arrays.asList(
                new TocParser.TocEntry("Repeat", 1, 0, 0),
                new TocParser.TocEntry("Repeat", 1, 3, 30));

        int offset = MarkdownActivity.resolveRenderedHeadingOffset(
                "Repeat\nBody\nRepeat\nBody", entries, 3);

        assertEquals(12, offset);
    }

    @Test
    public void resolveRenderedHeadingOffset_prefersHeadingLineOverBodyMention() {
        List<TocParser.TocEntry> entries = Collections.singletonList(
                new TocParser.TocEntry("Second Heading", 2, 1, 0));

        int offset = MarkdownActivity.resolveRenderedHeadingOffset(
                "Intro mentions Second Heading\nSecond Heading\nBody", entries, 1);

        assertEquals(30, offset);
    }
}
