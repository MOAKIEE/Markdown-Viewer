package com.example.markdownviewer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;

public class FileUtils {

    public static String getDisplayName(Context context, Uri uri) {
        if (uri == null) return "Untitled";

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        String name = cursor.getString(idx);
                        if (!TextUtils.isEmpty(name)) return name;
                    }
                }
            } catch (Exception ignored) {}
        }

        String path = uri.getLastPathSegment();
        if (path != null) {
            int lastSlash = path.lastIndexOf('/');
            return lastSlash != -1 ? path.substring(lastSlash + 1) : path;
        }
        return "Untitled";
    }
}