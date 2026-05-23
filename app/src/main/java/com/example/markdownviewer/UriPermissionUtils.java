package com.example.markdownviewer;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public final class UriPermissionUtils {

    private static final String TAG = "UriPermissionUtils";

    private UriPermissionUtils() {}

    static int readPersistableFlags() {
        return Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
    }

    public static void addReadPersistableFlags(Intent intent) {
        if (intent != null) {
            intent.addFlags(readPersistableFlags());
        }
    }

    public static boolean takeReadPermission(ContentResolver resolver, Uri uri) {
        if (resolver == null || uri == null || !"content".equals(uri.getScheme())) {
            return false;
        }
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return true;
        } catch (SecurityException e) {
            Log.w(TAG, "Read URI permission is not persistable: " + uri, e);
            return false;
        }
    }
}
