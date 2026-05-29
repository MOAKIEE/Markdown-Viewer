package com.example.markdownviewer.data;

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
