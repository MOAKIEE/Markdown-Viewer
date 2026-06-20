package com.example.markdownviewer;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class FilePickerActivityTest {

    @Test
    public void loadFilesAsync_snapshotsParentBeforeBackgroundWork() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/example/markdownviewer/FilePickerActivity.java")),
                StandardCharsets.UTF_8);

        int methodStart = source.indexOf("private void loadFilesAsync(Uri uri, String documentId)");
        int backgroundWork = source.indexOf("AppExecutor.getInstance().diskIO().execute", methodStart);
        int parentSnapshot = source.indexOf("final String parentDocumentId", methodStart);

        assertTrue(methodStart >= 0);
        assertTrue(backgroundWork >= 0);
        assertTrue(parentSnapshot >= 0);
        assertTrue(parentSnapshot < backgroundWork);
        assertTrue(source.contains("if (parentDocumentId != null)"));
    }
}
