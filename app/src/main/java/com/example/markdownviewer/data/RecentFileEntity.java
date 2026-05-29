package com.example.markdownviewer.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Objects;

/**
 * Room 实体：最近打开的文件记录。
 */
@Entity(tableName = "recent_files")
public class RecentFileEntity {

    @PrimaryKey
    @NonNull
    private String uri;

    private String name;

    private int scrollY;

    private long openedAt;

    public RecentFileEntity(@NonNull String uri, String name, int scrollY, long openedAt) {
        this.uri = uri;
        this.name = name;
        this.scrollY = scrollY;
        this.openedAt = openedAt;
    }

    @NonNull
    public String getUri() { return uri; }
    public void setUri(@NonNull String uri) { this.uri = uri; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getScrollY() { return scrollY; }
    public void setScrollY(int scrollY) { this.scrollY = scrollY; }

    public long getOpenedAt() { return openedAt; }
    public void setOpenedAt(long openedAt) { this.openedAt = openedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecentFileEntity that = (RecentFileEntity) o;
        return scrollY == that.scrollY &&
                openedAt == that.openedAt &&
                Objects.equals(uri, that.uri) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, name, scrollY, openedAt);
    }
}
