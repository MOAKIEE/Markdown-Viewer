package com.example.markdownviewer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

public class FileUtils {

    private static final String TAG = "FileUtils";

    public static String getDisplayName(Context context, Uri uri) {
        if (uri == null) return context.getString(R.string.recent_files_untitled);

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        String name = cursor.getString(idx);
                        if (!TextUtils.isEmpty(name)) return name;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to query display name", e);
            }
        }

        String path = uri.getLastPathSegment();
        if (path != null) {
            int lastSlash = path.lastIndexOf('/');
            return lastSlash != -1 ? path.substring(lastSlash + 1) : path;
        }
        return context.getString(R.string.recent_files_untitled);
    }
}
