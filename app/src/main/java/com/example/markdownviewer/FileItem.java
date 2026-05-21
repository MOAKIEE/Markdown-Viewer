package com.example.markdownviewer;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public class FileItem implements Parcelable {
    private String name;
    private boolean directory;
    private boolean parent;
    private String documentId;
    private long size;

    public FileItem(String name, boolean directory, boolean parent) {
        this(name, null, directory, parent, 0);
    }

    public FileItem(String name, String documentId, boolean directory, boolean parent, long size) {
        this.name = name;
        this.documentId = documentId;
        this.directory = directory;
        this.parent = parent;
        this.size = size;
    }

    protected FileItem(Parcel in) {
        name = in.readString();
        directory = in.readByte() != 0;
        parent = in.readByte() != 0;
        documentId = in.readString();
        size = in.readLong();
    }

    public static final Creator<FileItem> CREATOR = new Creator<FileItem>() {
        @Override
        public FileItem createFromParcel(Parcel in) {
            return new FileItem(in);
        }

        @Override
        public FileItem[] newArray(int size) {
            return new FileItem[size];
        }
    };

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return directory;
    }

    public boolean isParent() {
        return parent;
    }

    public String getDocumentId() {
        return documentId;
    }

    public long getSize() {
        return size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileItem fileItem = (FileItem) o;
        return directory == fileItem.directory
                && parent == fileItem.parent
                && size == fileItem.size
                && Objects.equals(name, fileItem.name)
                && Objects.equals(documentId, fileItem.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, directory, parent, documentId, size);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeByte((byte) (directory ? 1 : 0));
        dest.writeByte((byte) (parent ? 1 : 0));
        dest.writeString(documentId);
        dest.writeLong(size);
    }
}
