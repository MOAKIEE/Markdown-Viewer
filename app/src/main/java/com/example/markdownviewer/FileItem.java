package com.example.markdownviewer;

public class FileItem {
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
}
