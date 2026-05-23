package com.example.markdownviewer;

import android.content.res.Configuration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SystemBarUtilsTest {

    @Test
    public void resolveInsetsPadding_preservesExistingPaddingWhenAddingStatusBarInset() {
        SystemBarUtils.Padding padding = SystemBarUtils.resolveInsetsPadding(
                new SystemBarUtils.Padding(12, 12, 12, 12),
                24,
                0,
                true,
                false);

        assertEquals(12, padding.left);
        assertEquals(36, padding.top);
        assertEquals(12, padding.right);
        assertEquals(12, padding.bottom);
    }

    @Test
    public void resolveInsetsPadding_preservesExistingPaddingWhenAddingNavigationBarInset() {
        SystemBarUtils.Padding padding = SystemBarUtils.resolveInsetsPadding(
                new SystemBarUtils.Padding(8, 10, 8, 10),
                0,
                32,
                false,
                true);

        assertEquals(8, padding.left);
        assertEquals(10, padding.top);
        assertEquals(8, padding.right);
        assertEquals(42, padding.bottom);
    }

    @Test
    public void resolveInsetsMargins_preservesButtonPaddingByMovingViewAwayFromStatusBar() {
        SystemBarUtils.EdgeInsets margins = SystemBarUtils.resolveInsetsMargins(
                new SystemBarUtils.EdgeInsets(0, 16, 16, 0),
                24,
                0,
                true,
                false);

        assertEquals(0, margins.left);
        assertEquals(40, margins.top);
        assertEquals(16, margins.right);
        assertEquals(0, margins.bottom);
    }

    @Test
    public void shouldUseLightSystemBars_returnsFalseForNightMode() {
        boolean lightBars = SystemBarUtils.shouldUseLightSystemBars(
                Configuration.UI_MODE_NIGHT_YES);

        assertEquals(false, lightBars);
    }

    @Test
    public void shouldUseLightSystemBars_returnsTrueForNotNightMode() {
        boolean lightBars = SystemBarUtils.shouldUseLightSystemBars(
                Configuration.UI_MODE_NIGHT_NO);

        assertEquals(true, lightBars);
    }
}
