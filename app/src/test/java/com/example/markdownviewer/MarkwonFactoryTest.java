package com.example.markdownviewer;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

public class MarkwonFactoryTest {

    @Test
    public void sanitizeHtml_null_returnsNull() {
        assertNull(MarkwonFactory.sanitizeHtml(null));
    }

    @Test
    public void sanitizeHtml_plainText_unchanged() {
        String input = "Hello World";
        assertEquals(input, MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_safeTags_preserved() {
        String input = "<p>Hello <b>World</b></p>";
        assertEquals(input, MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_scriptTag_removed() {
        String input = "<p>Hello</p><script>alert(1)</script>";
        // jsoup 移除 script 标签及其内容
        assertEquals("<p>Hello</p>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_styleTag_removed() {
        String input = "<p>Hello</p><style>body{color:red}</style>";
        assertEquals("<p>Hello</p>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_eventHandler_removed() {
        String input = "<img src=\"x\" onclick=\"alert(1)\" />";
        // jsoup 保留 img 标签但移除 onclick 属性，自闭合标签格式可能变化
        String result = MarkwonFactory.sanitizeHtml(input);
        assertTrue(result.contains("<img"));
        assertTrue(result.contains("src=\"x\""));
        assertFalse(result.contains("onclick"));
    }

    @Test
    public void sanitizeHtml_javascriptUrl_blocked() {
        String input = "<a href=\"javascript:alert(1)\">Click</a>";
        // jsoup 自动移除 javascript: URL（href 被清空）
        String result = MarkwonFactory.sanitizeHtml(input);
        assertTrue(result.contains("<a"));
        assertTrue(result.contains(">Click</a>"));
        assertFalse(result.contains("javascript"));
    }

    @Test
    public void sanitizeHtml_uppercaseJavascriptUrl_blockedInTurkishLocale() {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            String input = "<a href=\"JAVASCRIPT:alert(1)\">Click</a>"
                    + "<img src=\"JAVASCRIPT:alert(1)\" />";

            String result = MarkwonFactory.sanitizeHtml(input);

            assertFalse(result.toLowerCase(Locale.ROOT).contains("javascript:"));
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    public void sanitizeHtml_vbscriptUrl_blocked() {
        String input = "<a href=\"vbscript:msgbox(1)\">Click</a>";
        String result = MarkwonFactory.sanitizeHtml(input);
        assertFalse(result.contains("vbscript"));
    }

    @Test
    public void sanitizeHtml_dataTextHtmlUrl_blocked() {
        String input = "<img src=\"data:text/html,<script>alert(1)</script>\" />";
        // 自定义后处理会清空恶意的 data: URL
        String result = MarkwonFactory.sanitizeHtml(input);
        assertTrue(result.contains("<img"));
        assertFalse(result.contains("data:text/html"));
    }

    @Test
    public void sanitizeHtml_safeDataImageUrl_allowed() {
        String input = "<img src=\"data:image/png;base64,abc123\" />";
        String result = MarkwonFactory.sanitizeHtml(input);
        assertTrue(result.contains("data:image/png"));
    }

    @Test
    public void sanitizeHtml_dataSvgImageUrl_blocked() {
        // SVG 内嵌可执行脚本，是 data: URL 漏洞的真实载体，必须拦截
        String input = "<img src=\"data:image/svg+xml,<svg onload=alert(1)>\" />";
        String result = MarkwonFactory.sanitizeHtml(input);
        assertTrue(result.contains("<img"));
        assertFalse(result.contains("data:image/svg+xml"));
        assertFalse(result.contains("onload"));
    }

    @Test
    public void sanitizeHtml_dangerousAttrs_removed() {
        String input = "<a href=\"https://example.com\" target=\"_blank\" download=\"file.exe\">Link</a>";
        String result = MarkwonFactory.sanitizeHtml(input);
        assertTrue(result.contains("href=\"https://example.com\""));
        assertFalse(result.contains("target"));
        assertFalse(result.contains("download"));
    }

    @Test
    public void sanitizeHtml_htmlComment_removed() {
        String input = "<!-- comment --><p>Text</p>";
        assertEquals("<p>Text</p>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_doctype_removed() {
        String input = "<!DOCTYPE html><p>Text</p>";
        assertEquals("<p>Text</p>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_unsafeTag_removed() {
        String input = "<p>Safe</p><iframe src=\"evil.com\"></iframe>";
        assertEquals("<p>Safe</p>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_nestedUnsafe_removed() {
        String input = "<div><script>alert(1)</script><p>Safe</p></div>";
        String result = MarkwonFactory.sanitizeHtml(input);
        assertTrue(result.contains("<div>"));
        assertTrue(result.contains("<p>Safe</p>"));
        assertFalse(result.contains("script"));
    }

    @Test
    public void contentNeedsLatex_withDoubleDollar_returnsTrue() {
        assertTrue(MarkwonFactory.contentNeedsLatex("$$x^2$$"));
    }

    @Test
    public void contentNeedsLatex_withInlineMath_returnsTrue() {
        assertTrue(MarkwonFactory.contentNeedsLatex("\\(x^2\\)"));
    }

    @Test
    public void contentNeedsLatex_withDisplayMath_returnsTrue() {
        assertTrue(MarkwonFactory.contentNeedsLatex("\\[x^2\\]"));
    }

    @Test
    public void contentNeedsLatex_noMath_returnsFalse() {
        assertFalse(MarkwonFactory.contentNeedsLatex("# Hello World\n\nThis is markdown."));
    }

    @Test
    public void contentNeedsLatex_null_returnsFalse() {
        assertFalse(MarkwonFactory.contentNeedsLatex(null));
    }
}
