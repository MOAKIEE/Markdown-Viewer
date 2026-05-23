package com.example.markdownviewer;

import android.content.Intent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UriPermissionUtilsTest {

    @Test
    public void readPersistableFlags_includeReadAndPersistableGrant() {
        int flags = UriPermissionUtils.readPersistableFlags();

        int expected = Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        assertEquals(expected, flags);
    }
}
