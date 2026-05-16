package com.example.markdownviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class RecentFilesManager {
    private static final String PREFS_NAME = "MarkdownViewerPrefs";
    private static final String KEY_RECENT = "recent_files_ordered";
    private static final int MAX_RECENT = 5;
    private static final String DELIM = "\n";
    private static final String FIELD_SEP = "|";

    public static void addRecentFile(Context context, Uri uri) {
        if (uri == null) return;
        String uriString = uri.toString();
        String displayName = FileUtils.getDisplayName(context, uri);
        List<RecentEntry> list = loadList(context);

        // Remove if exists
        list.removeIf(e -> e.uri.equals(uriString));
        // Insert at front
        list.add(0, new RecentEntry(uriString, displayName));
        // Trim
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
        saveList(context, list);
    }

    public static List<RecentEntry> getRecentFiles(Context context) {
        List<RecentEntry> list = loadList(context);
        List<RecentEntry> validList = new ArrayList<>();
        boolean hasInvalid = false;
        for (RecentEntry entry : list) {
            if (isValid(context, entry.uri)) {
                validList.add(entry);
            } else {
                hasInvalid = true;
            }
        }
        if (hasInvalid) {
            saveList(context, validList);
        }
        return validList;
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_RECENT).apply();
    }

    private static List<RecentEntry> loadList(Context context) {
        List<RecentEntry> result = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_RECENT, "");
        if (TextUtils.isEmpty(raw)) return result;
        String[] lines = raw.split(DELIM, -1);
        for (String line : lines) {
            int sep = line.indexOf(FIELD_SEP);
            if (sep < 0) continue;
            String uri = line.substring(0, sep);
            String name = line.substring(sep + 1);
            result.add(new RecentEntry(uri, name));
        }
        return result;
    }

    private static void saveList(Context context, List<RecentEntry> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(DELIM);
            sb.append(list.get(i).uri).append(FIELD_SEP).append(list.get(i).name);
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_RECENT, sb.toString()).apply();
    }

    private static boolean isValid(Context context, String uriString) {
        if (TextUtils.isEmpty(uriString)) return false;
        Uri uri = Uri.parse(uriString);
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                return cursor != null && cursor.moveToFirst();
            } catch (Exception e) {
                return false;
            }
        } else if ("file".equals(uri.getScheme())) {
            java.io.File file = new java.io.File(uri.getPath());
            return file.exists();
        }
        return true;
    }

    public static class RecentEntry {
        public final String uri;
        public final String name;

        public RecentEntry(String uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }
}
