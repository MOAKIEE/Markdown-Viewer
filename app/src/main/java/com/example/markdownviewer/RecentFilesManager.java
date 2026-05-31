package com.example.markdownviewer;

import android.content.Context;
import android.net.Uri;

import com.example.markdownviewer.data.AppDatabase;
import com.example.markdownviewer.data.RecentFileDao;
import com.example.markdownviewer.data.RecentFileEntity;

import java.util.ArrayList;
import java.util.List;

public class RecentFilesManager {

    private static final Object sLock = new Object();

    public interface RecentFilesCallback {
        void onResult(List<RecentEntry> entries);
    }

    public interface ScrollYCallback {
        void onResult(int scrollY);
    }

    /**
     * 获取最近文件列表（同步读取，线程安全）。
     */
    public static List<RecentEntry> getRecentFiles(Context context) {
        synchronized (sLock) {
            RecentFileDao dao = AppDatabase.getInstance(context).recentFileDao();
            List<RecentFileEntity> entities = dao.getRecentFiles(Constants.MAX_RECENT);
            List<RecentEntry> result = new ArrayList<>(entities.size());
            for (RecentFileEntity entity : entities) {
                result.add(new RecentEntry(entity.getUri(), entity.getName(), entity.getScrollY()));
            }
            return result;
        }
    }

    public static void getRecentFilesAsync(Context context, RecentFilesCallback callback) {
        if (context == null || callback == null) return;
        AppExecutor.getInstance().diskIO().execute(() -> {
            List<RecentEntry> entries = getRecentFiles(context.getApplicationContext());
            AppExecutor.getInstance().mainThread().post(() -> callback.onResult(entries));
        });
    }

    /**
     * 添加或更新最近文件记录。写操作在后台线程执行。
     */
    public static void addRecentFile(Context context, Uri uri) {
        if (uri == null) return;
        String uriString = uri.toString();
        String displayName = FileUtils.getDisplayName(context, uri);

        AppExecutor.getInstance().diskIO().execute(() -> {
            RecentFileDao dao = AppDatabase.getInstance(context).recentFileDao();
            RecentFileEntity existing = dao.getByUri(uriString);
            int scrollY = existing != null ? existing.getScrollY() : 0;
            dao.insert(new RecentFileEntity(uriString, displayName, scrollY, System.currentTimeMillis()));

            // 限制最大记录数
            if (dao.getCount() > Constants.MAX_RECENT) {
                dao.trimOldRecords(Constants.MAX_RECENT);
            }
        });
    }

    /**
     * 更新文件的滚动位置。写操作在后台线程执行。
     */
    public static void updateScrollY(Context context, Uri uri, int scrollY) {
        if (uri == null) return;
        String uriString = uri.toString();

        AppExecutor.getInstance().diskIO().execute(() -> {
            RecentFileDao dao = AppDatabase.getInstance(context).recentFileDao();
            RecentFileEntity existing = dao.getByUri(uriString);
            if (existing != null && existing.getScrollY() != scrollY) {
                existing.setScrollY(scrollY);
                dao.update(existing);
            }
        });
    }

    public static int getScrollY(Context context, Uri uri) {
        if (uri == null) return 0;
        synchronized (sLock) {
            RecentFileDao dao = AppDatabase.getInstance(context).recentFileDao();
            RecentFileEntity entity = dao.getByUri(uri.toString());
            return entity != null ? entity.getScrollY() : 0;
        }
    }

    public static void getScrollYAsync(Context context, Uri uri, ScrollYCallback callback) {
        if (context == null || callback == null) return;
        AppExecutor.getInstance().diskIO().execute(() -> {
            int scrollY = getScrollY(context.getApplicationContext(), uri);
            AppExecutor.getInstance().mainThread().post(() -> callback.onResult(scrollY));
        });
    }

    static List<RecentEntry> limitRecentFiles(List<RecentEntry> entries, int maxCount) {
        if (entries == null || maxCount <= 0) {
            return new ArrayList<>();
        }
        int count = Math.min(entries.size(), maxCount);
        return new ArrayList<>(entries.subList(0, count));
    }

    public static void clear(Context context) {
        AppExecutor.getInstance().diskIO().execute(() -> {
            AppDatabase.getInstance(context).recentFileDao().deleteAll();
        });
    }

    public static void removeRecentFile(Context context, String uriString) {
        if (uriString == null) return;
        AppExecutor.getInstance().diskIO().execute(() -> {
            AppDatabase.getInstance(context).recentFileDao().deleteByUri(uriString);
        });
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
