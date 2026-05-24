package com.example.markdownviewer;

import android.net.Uri;
import android.text.Spanned;

import androidx.lifecycle.ViewModel;

import java.util.List;

public class MarkdownViewModel extends ViewModel {

    @Override
    protected void onCleared() {
        super.onCleared();
        rawMarkdownContent = null;
        renderedContent = null;
        tocEntries = null;
        fileUri = null;
        title = null;
    }

    private String rawMarkdownContent;
    private Spanned renderedContent;
    private List<TocParser.TocEntry> tocEntries;
    private Uri fileUri;
    private String title;
    private int scrollY;

    public String getRawMarkdownContent() { return rawMarkdownContent; }
    public void setRawMarkdownContent(String content) { this.rawMarkdownContent = content; }

    public Spanned getRenderedContent() { return renderedContent; }
    public void setRenderedContent(Spanned content) { this.renderedContent = content; }

    public List<TocParser.TocEntry> getTocEntries() { return tocEntries; }
    public void setTocEntries(List<TocParser.TocEntry> entries) { this.tocEntries = entries; }

    public Uri getFileUri() { return fileUri; }
    public void setFileUri(Uri uri) { this.fileUri = uri; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getScrollY() { return scrollY; }
    public void setScrollY(int scrollY) { this.scrollY = scrollY; }

    public boolean hasContent() {
        return rawMarkdownContent != null && !rawMarkdownContent.isEmpty();
    }
}
