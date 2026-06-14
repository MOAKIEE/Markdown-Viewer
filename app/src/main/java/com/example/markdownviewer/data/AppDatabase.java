package com.example.markdownviewer.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {RecentFileEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final String DB_NAME = "markdown_viewer.db";

    public abstract RecentFileDao recentFileDao();

    public static AppDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME)
                            // 仅在降级（如回滚到旧版本）时清表；升级强制显式 Migration，
                            // 避免未来加字段时静默丢失用户最近文件与阅读进度。
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 仅用于测试：允许注入 mock 数据库。
     */
    static void setInstance(AppDatabase instance) {
        INSTANCE = instance;
    }
}
