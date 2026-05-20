package com.example.markdownviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史最近打开文件管理器。
 * 通过 SharedPreferences 持久化存储最近文件的 JSON 列表。
 * 已扩展支持“自动记忆阅读进度 (scrollY)”属性。
 */
public class RecentFilesManager {
    private static final String PREFS_NAME = "MarkdownViewerPrefs";
    private static final String KEY_RECENT = "recent_files_json";

    private static final String JSON_KEY_URI = "uri";
    private static final String JSON_KEY_NAME = "name";
    private static final String JSON_KEY_SCROLL_Y = "scroll_y"; // 💡 进度持久化字段

    public static void addRecentFile(Context context, Uri uri) {
        if (uri == null) return;
        String uriString = uri.toString();
        String displayName = FileUtils.getDisplayName(context, uri);
        List<RecentEntry> list = loadList(context);

        // 💡 检查该文件是否原本已在列表中，保留其原有的阅读位置，避免被覆盖清零
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
        saveList(context, list);
    }

    /**
     * 💡 新增：更新指定 URI 历史记录的滚动阅读位置
     */
    public static void updateScrollY(Context context, Uri uri, int scrollY) {
        if (uri == null) return;
        String uriString = uri.toString();
        List<RecentEntry> list = loadList(context);
        boolean changed = false;

        for (int i = 0; i < list.size(); i++) {
            RecentEntry old = list.get(i);
            if (old.uri.equals(uriString)) {
                list.set(i, new RecentEntry(old.uri, old.name, scrollY));
                changed = true;
                break;
            }
        }

        if (changed) {
            saveList(context, list);
        }
    }

    /**
     * 💡 新增：获取指定 URI 历史记录的滚动阅读位置
     */
    public static int getScrollY(Context context, Uri uri) {
        if (uri == null) return 0;
        String uriString = uri.toString();
        List<RecentEntry> list = loadList(context);
        for (RecentEntry entry : list) {
            if (entry.uri.equals(uriString)) {
                return entry.scrollY;
            }
        }
        return 0;
    }

    public static List<RecentEntry> getRecentFiles(Context context) {
        return loadList(context);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_RECENT).apply();
    }

    public static void removeRecentFile(Context context, String uriString) {
        if (uriString == null) return;
        List<RecentEntry> list = loadList(context);
        list.removeIf(e -> e.uri.equals(uriString));
        saveList(context, list);
    }

    private static List<RecentEntry> loadList(Context context) {
        List<RecentEntry> result = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_RECENT, "");
        if (TextUtils.isEmpty(raw)) return result;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                // 💡 采用 optInt 读取，对不包含该字段的旧记录天然向后兼容，默认返回 0
                int scrollY = obj.optInt(JSON_KEY_SCROLL_Y, 0);
                result.add(new RecentEntry(obj.getString(JSON_KEY_URI), obj.getString(JSON_KEY_NAME), scrollY));
            }
        } catch (JSONException ignored) {}
        return result;
    }

    private static void saveList(Context context, List<RecentEntry> list) {
        JSONArray arr = new JSONArray();
        for (RecentEntry entry : list) {
            JSONObject obj = new JSONObject();
            try {
                obj.put(JSON_KEY_URI, entry.uri);
                obj.put(JSON_KEY_NAME, entry.name);
                obj.put(JSON_KEY_SCROLL_Y, entry.scrollY); // 💡 保存进度
            } catch (JSONException ignored) {}
            arr.put(obj);
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_RECENT, arr.toString()).apply();
    }

    public static class RecentEntry {
        public final String uri;
        public final String name;
        public final int scrollY; // 💡 滚动位置

        public RecentEntry(String uri, String name) {
            this(uri, name, 0);
        }

        public RecentEntry(String uri, String name, int scrollY) {
            this.uri = uri;
            this.name = name;
            this.scrollY = scrollY;
        }
    }
}
