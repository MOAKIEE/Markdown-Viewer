package com.example.markdownviewer.data;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RecentFileDao {

    @Query("SELECT * FROM recent_files ORDER BY openedAt DESC LIMIT :limit")
    List<RecentFileEntity> getRecentFiles(int limit);

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    RecentFileEntity getByUri(String uri);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RecentFileEntity entity);

    /**
     * 原子地刷新既有记录的展示名与打开时间，保留 scrollY。
     * 用于"再次打开最近文件"场景，避免先查后写造成的 TOCTOU 丢失阅读进度。
     *
     * @return 受影响行数；0 表示该 uri 尚不存在，需调用 {@link #insert} 新建
     */
    @Query("UPDATE recent_files SET name = :name, openedAt = :openedAt WHERE uri = :uri")
    int touchTimestamp(@NonNull String uri, String name, long openedAt);

    @Update
    void update(RecentFileEntity entity);

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    void deleteByUri(String uri);

    @Query("DELETE FROM recent_files")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM recent_files")
    int getCount();

    @Query("DELETE FROM recent_files WHERE uri NOT IN (SELECT uri FROM recent_files ORDER BY openedAt DESC LIMIT :keepCount)")
    void trimOldRecords(int keepCount);
}
