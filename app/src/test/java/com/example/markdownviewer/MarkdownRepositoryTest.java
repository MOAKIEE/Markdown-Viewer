package com.example.markdownviewer;

import android.content.Context;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import io.noties.markwon.Markwon;

import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
public class MarkdownRepositoryTest {

    @Test
    public void loadMarkdownSync_rejectsStreamThatExceedsLimitWhenProviderSizeUnknown() {
        Context context = RuntimeEnvironment.getApplication();
        Uri uri = Uri.parse("content://com.example.markdownviewer.test/document/oversized.md");
        byte[] bytes = new byte[(int) Constants.MAX_FILE_SIZE + 1];
        Arrays.fill(bytes, (byte) 'a');
        Shadows.shadowOf(context.getContentResolver())
                .registerInputStream(uri, new ByteArrayInputStream(bytes));

        Markwon markwon = Markwon.create(context);
        MarkdownRepository.LoadResult result = MarkdownRepository.loadMarkdownSync(
                context, uri, markwon, new AtomicBoolean(false));

        assertFalse(result.success);
    }
}
