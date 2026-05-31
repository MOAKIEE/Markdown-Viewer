package com.example.markdownviewer;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
}
