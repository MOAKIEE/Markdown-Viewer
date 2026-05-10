package com.example.markdownviewer;

public class FileItem {
    private String name;
    private boolean directory;
    private boolean parent;

    public FileItem(String name, boolean directory, boolean parent) {
        this.name = name;
        this.directory = directory;
        this.parent = parent;
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
}
