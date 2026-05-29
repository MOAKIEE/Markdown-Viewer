package com.example.markdownviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class RecentFilesManager {
    private static final String TAG = "RecentFiles";
    private static final String PREFS_NAME = "MarkdownViewerSecurePrefs";
    private static final String KEY_RECENT = "recent_files_json";

    private static final String JSON_KEY_URI = "uri";
    private static final String JSON_KEY_NAME = "name";
    private static final String JSON_KEY_SCROLL_Y = "scroll_y";

    private static final Object sLock = new Object();
    private static volatile List<RecentEntry> sCache;

    /**
     * 获取加密 SharedPreferences 实例。
     * 如果加密初始化失败（如设备不支持），回退到普通 SharedPreferences。
     */
    private static SharedPreferences getSecurePrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.w(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to plain", e);
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public static void addRecentFile(Context context, Uri uri) {
        if (uri == null) return;
        String uriString = uri.toString();
        String displayName = FileUtils.getDisplayName(context, uri);
        synchronized (sLock) {
            List<RecentEntry> list = loadListLocked(context);

            int existingScrollY = 0;
            for (RecentEntry entry : list) {
                if (entry.uri.equals(uriString)) {
                    existingScrollY = entry.scrollY;
                    break;
                }
            }

            list.removeIf(e -> e.uri.equals(uriString));
            list.add(0, new RecentEntry(uriString, displayName, existingScrollY));
            while (list.size() > Constants.MAX_RECENT) {
                list.remove(list.size() - 1);
            }
            saveListLocked(context, list);
        }
    }

    public static void updateScrollY(Context context, Uri uri, int scrollY) {
        if (uri == null) return;
        String uriString = uri.toString();
        synchronized (sLock) {
            List<RecentEntry> list = loadListLocked(context);
            for (int i = 0; i < list.size(); i++) {
                RecentEntry old = list.get(i);
                if (old.uri.equals(uriString)) {
                    if (old.scrollY == scrollY) return;
                    list.set(i, new RecentEntry(old.uri, old.name, scrollY));
                    saveListLocked(context, list);
                    return;
                }
            }
        }
    }

    public static int getScrollY(Context context, Uri uri) {
        if (uri == null) return 0;
        String uriString = uri.toString();
        synchronized (sLock) {
            ensureCacheLoaded(context);
            for (RecentEntry entry : sCache) {
                if (entry.uri.equals(uriString)) {
                    return entry.scrollY;
                }
            }
        }
        return 0;
    }

    public static List<RecentEntry> getRecentFiles(Context context) {
        synchronized (sLock) {
            ensureCacheLoaded(context);
            return new ArrayList<>(sCache);
        }
    }

    static List<RecentEntry> limitRecentFiles(List<RecentEntry> entries, int maxCount) {
        if (entries == null || maxCount <= 0) {
            return new ArrayList<>();
        }
        int count = Math.min(entries.size(), maxCount);
        return new ArrayList<>(entries.subList(0, count));
    }

    public static void clear(Context context) {
        synchronized (sLock) {
            sCache = null;
            getSecurePrefs(context).edit().remove(KEY_RECENT).apply();
        }
    }

    public static void removeRecentFile(Context context, String uriString) {
        if (uriString == null) return;
        synchronized (sLock) {
            List<RecentEntry> list = loadListLocked(context);
            list.removeIf(e -> e.uri.equals(uriString));
            saveListLocked(context, list);
        }
    }

    private static void ensureCacheLoaded(Context context) {
        if (sCache != null) return;
        loadListLocked(context);
    }

    private static List<RecentEntry> loadListLocked(Context context) {
        if (sCache != null) {
            return new ArrayList<>(sCache);
        }
        List<RecentEntry> result = new ArrayList<>();
        SharedPreferences prefs = getSecurePrefs(context);
        String raw = prefs.getString(KEY_RECENT, "");
        if (TextUtils.isEmpty(raw)) {
            sCache = result;
            return new ArrayList<>(result);
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                int scrollY = obj.optInt(JSON_KEY_SCROLL_Y, 0);
                result.add(new RecentEntry(obj.getString(JSON_KEY_URI), obj.getString(JSON_KEY_NAME), scrollY));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse recent files", e);
        }
        sCache = result;
        return new ArrayList<>(result);
    }

    private static void saveListLocked(Context context, List<RecentEntry> list) {
        JSONArray arr = new JSONArray();
        for (RecentEntry entry : list) {
            JSONObject obj = new JSONObject();
            try {
                obj.put(JSON_KEY_URI, entry.uri);
                obj.put(JSON_KEY_NAME, entry.name);
                obj.put(JSON_KEY_SCROLL_Y, entry.scrollY);
            } catch (JSONException e) {
                Log.w(TAG, "Failed to serialize recent entry", e);
            }
            arr.put(obj);
        }
        sCache = new ArrayList<>(list);
        getSecurePrefs(context).edit().putString(KEY_RECENT, arr.toString()).apply();
    }

    public static class RecentEntry {
        public final String uri;
        public final String name;
        public final int scrollY;

        public RecentEntry(String uri, String name) {
            this(uri, name, 0);
        }

        public RecentEntry(String uri, String name, int scrollY) {
            this.uri = uri;
            this.name = name;
            this.scrollY = scrollY;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RecentEntry that = (RecentEntry) o;
            return scrollY == that.scrollY &&
                    java.util.Objects.equals(uri, that.uri) &&
                    java.util.Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(uri, name, scrollY);
        }
    }
}
