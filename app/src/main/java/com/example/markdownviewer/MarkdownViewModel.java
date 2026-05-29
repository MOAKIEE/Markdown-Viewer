package com.example.markdownviewer;

import android.net.Uri;
import android.text.Spanned;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class MarkdownViewModel extends ViewModel {

    private final MutableLiveData<String> rawMarkdownContent = new MutableLiveData<>();
    private final MutableLiveData<Spanned> renderedContent = new MutableLiveData<>();
    private final MutableLiveData<List<TocParser.TocEntry>> tocEntries = new MutableLiveData<>();
    private final MutableLiveData<Uri> fileUri = new MutableLiveData<>();
    private final MutableLiveData<String> title = new MutableLiveData<>();
    private final MutableLiveData<Integer> scrollY = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public LiveData<String> getRawMarkdownContent() { return rawMarkdownContent; }
    public LiveData<Spanned> getRenderedContent() { return renderedContent; }
    public LiveData<List<TocParser.TocEntry>> getTocEntries() { return tocEntries; }
    public LiveData<Uri> getFileUri() { return fileUri; }
    public LiveData<String> getTitle() { return title; }
    public LiveData<Integer> getScrollY() { return scrollY; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public void setRawMarkdownContent(String content) { rawMarkdownContent.setValue(content); }
    public void setRenderedContent(Spanned content) { renderedContent.setValue(content); }
    public void setTocEntries(List<TocParser.TocEntry> entries) { tocEntries.setValue(entries); }
    public void setFileUri(Uri uri) { fileUri.setValue(uri); }
    public void setTitle(String t) { title.setValue(t); }
    public void setScrollY(int y) { scrollY.setValue(y); }
    public void setIsLoading(boolean loading) { isLoading.setValue(loading); }
    public void setErrorMessage(String error) { errorMessage.setValue(error); }

    public boolean hasContent() {
        String content = rawMarkdownContent.getValue();
        return content != null && !content.isEmpty();
    }

    public void clear() {
        rawMarkdownContent.setValue(null);
        renderedContent.setValue(null);
        tocEntries.setValue(null);
        fileUri.setValue(null);
        title.setValue(null);
        scrollY.setValue(0);
        isLoading.setValue(false);
        errorMessage.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        clear();
    }
}
