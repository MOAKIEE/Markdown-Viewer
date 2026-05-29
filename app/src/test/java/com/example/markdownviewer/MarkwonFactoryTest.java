package com.example.markdownviewer;

import org.junit.Test;

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
        assertEquals("<p>Hello</p>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_eventHandler_removed() {
        String input = "<img src=\"x\" onclick=\"alert(1)\" />";
        assertEquals("<img src=\"x\" />", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_javascriptUrl_blocked() {
        String input = "<a href=\"javascript:alert(1)\">Click</a>";
        assertEquals("<a href=\"#blocked\">Click</a>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_dataUrl_blocked() {
        String input = "<img src=\"data:text/html,<script>alert(1)</script>\" />";
        assertEquals("<img src=\"#blocked\" />", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_styleWithExpression_removed() {
        String input = "<div style=\"width: expression(alert(1))\">Text</div>";
        assertEquals("<div>Text</div>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_dangerousAttrs_removed() {
        String input = "<a href=\"https://example.com\" target=\"_blank\" download=\"file.exe\">Link</a>";
        assertEquals("<a href=\"https://example.com\">Link</a>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_htmlComment_removed() {
        String input = "<!-- comment --><p>Text</p>";
        assertEquals("<p>Text</p>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_cdata_removed() {
        String input = "<![CDATA[ <script>alert(1)</script> ]]><p>Text</p>";
        assertEquals("<p>Text</p>", MarkwonFactory.sanitizeHtml(input));
    }

    @Test
    public void sanitizeHtml_doctype_removed() {
        String input = "<!DOCTYPE html><p>Text</p>";
        assertEquals("<p>Text</p>", MarkwonFactory.sanitizeHtml(input));
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
