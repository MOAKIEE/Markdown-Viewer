package com.example.markdownviewer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.Spanned;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.noties.markwon.Markwon;

/**
 * 负责 Markdown 文件的加载、清理和渲染。
 * 所有耗时操作均在后台线程执行。
 */
public final class MarkdownRepository {

    private static final String TAG = "MarkdownRepository";

    public static final class LoadResult {
        public final String rawMarkdown;
        public final Spanned renderedContent;
        public final List<TocParser.TocEntry> tocEntries;
        public final String title;
        public final boolean success;
        public final String errorMessage;

        private LoadResult(String rawMarkdown, Spanned renderedContent,
                           List<TocParser.TocEntry> tocEntries, String title) {
            this.rawMarkdown = rawMarkdown;
            this.renderedContent = renderedContent;
            this.tocEntries = tocEntries;
            this.title = title;
            this.success = true;
            this.errorMessage = null;
        }

        private LoadResult(String errorMessage) {
            this.rawMarkdown = null;
            this.renderedContent = null;
            this.tocEntries = null;
            this.title = null;
            this.success = false;
            this.errorMessage = errorMessage;
        }

        public static LoadResult success(String rawMarkdown, Spanned renderedContent,
                                          List<TocParser.TocEntry> tocEntries, String title) {
            return new LoadResult(rawMarkdown, renderedContent, tocEntries, title);
        }

        public static LoadResult failure(String errorMessage) {
            return new LoadResult(errorMessage);
        }
    }

    private MarkdownRepository() {}

    /**
     * 从 URI 异步加载 Markdown 内容。
     *
     * @param context    上下文
     * @param uri        content:// URI
     * @param markwon    已配置的 Markwon 实例（用于渲染）
     * @param callback   结果回调（在主线程执行）
     * @param cancelled  取消标记
     */
    public static void loadMarkdownAsync(@NonNull Context context,
                                          @NonNull Uri uri,
                                          @NonNull Markwon markwon,
                                          @NonNull LoadCallback callback,
                                          @NonNull AtomicBoolean cancelled) {
        AppExecutor.getInstance().diskIO().execute(() -> {
            LoadResult result = loadMarkdownSync(context, uri, markwon, cancelled);
            if (!cancelled.get()) {
                AppExecutor.getInstance().mainThread().post(() -> callback.onResult(result));
            }
        });
    }

    @NonNull
    static LoadResult loadMarkdownSync(@NonNull Context context,
                                        @NonNull Uri uri,
                                        @NonNull Markwon markwon,
                                        @NonNull AtomicBoolean cancelled) {
        if (!"content".equals(uri.getScheme())) {
            return LoadResult.failure("Unsupported URI scheme");
        }

        // 查询文件大小
        long fileSize = 0;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    fileSize = cursor.getLong(sizeIdx);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query file size", e);
        }

        if (fileSize > Constants.MAX_FILE_SIZE) {
            return LoadResult.failure("File too large");
        }

        // 读取内容
        StringBuilder content = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return LoadResult.failure("Failed to open file");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled.get()) {
                        return LoadResult.failure("Cancelled");
                    }
                    content.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file", e);
            return LoadResult.failure("Failed to read file");
        }

        // 清理 HTML
        String markdown = MarkwonFactory.sanitizeHtml(content.toString());
        if (markdown == null) markdown = "";

        // 解析目录
        List<TocParser.TocEntry> toc = TocParser.parse(markdown);

        // 渲染 Markdown
        Spanned rendered;
        try {
            rendered = markwon.toMarkdown(markdown);
        } catch (Exception e) {
            Log.e(TAG, "Failed to render markdown", e);
            return LoadResult.failure("Failed to render markdown");
        }

        String title = FileUtils.getDisplayName(context, uri);
        return LoadResult.success(markdown, rendered, toc, title);
    }

    @Nullable
    static String readRawMarkdownSync(@NonNull Context context, @NonNull Uri uri,
                                       @NonNull AtomicBoolean cancelled) {
        if (!"content".equals(uri.getScheme())) return null;

        StringBuilder content = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled.get()) return null;
                    content.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file", e);
            return null;
        }
        return MarkwonFactory.sanitizeHtml(content.toString());
    }

    public interface LoadCallback {
        void onResult(@NonNull LoadResult result);
    }
}
