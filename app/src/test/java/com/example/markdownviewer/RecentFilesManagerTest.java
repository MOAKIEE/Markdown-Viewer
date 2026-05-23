package com.example.markdownviewer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RecentFilesManagerTest {

    @Test
    public void limitRecentFiles_returnsIndependentLimitedCopy() {
        List<RecentFilesManager.RecentEntry> entries = new ArrayList<>();
        entries.add(new RecentFilesManager.RecentEntry("content://one", "one"));
        entries.add(new RecentFilesManager.RecentEntry("content://two", "two"));
        entries.add(new RecentFilesManager.RecentEntry("content://three", "three"));

        List<RecentFilesManager.RecentEntry> limited =
                RecentFilesManager.limitRecentFiles(entries, 2);
        entries.remove(0);

        assertEquals(2, limited.size());
        assertEquals("content://one", limited.get(0).uri);
        assertEquals("content://two", limited.get(1).uri);
    }
}
