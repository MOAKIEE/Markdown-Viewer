package com.example.markdownviewer;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TocParserTest {

    @Test
    public void parse_null_returnsEmptyList() {
        List<TocParser.TocEntry> entries = TocParser.parse(null);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void parse_emptyString_returnsEmptyList() {
        List<TocParser.TocEntry> entries = TocParser.parse("");
        assertTrue(entries.isEmpty());
    }

    @Test
    public void parse_singleHeading_extractsCorrectly() {
        List<TocParser.TocEntry> entries = TocParser.parse("# Hello World");
        assertEquals(1, entries.size());
        assertEquals("Hello World", entries.get(0).title);
        assertEquals(1, entries.get(0).level);
        assertEquals(0, entries.get(0).lineIndex);
        assertEquals(0, entries.get(0).charOffset);
    }

    @Test
    public void parse_multipleHeadings_extractsAll() {
        String content = "# H1\n## H2\n### H3\n#### H4\n##### H5\n###### H6";
        List<TocParser.TocEntry> entries = TocParser.parse(content);
        assertEquals(6, entries.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(i + 1, entries.get(i).level);
        }
    }

    @Test
    public void parse_headingsInCodeBlock_ignored() {
        String content = "```\n# Not a heading\n```\n# Real Heading";
        List<TocParser.TocEntry> entries = TocParser.parse(content);
        assertEquals(1, entries.size());
        assertEquals("Real Heading", entries.get(0).title);
    }

    @Test
    public void parse_headingsInIndentedCodeBlock_ignored() {
        String content = "   ```\n   # Not a heading\n   ```\n# Real Heading";
        List<TocParser.TocEntry> entries = TocParser.parse(content);
        assertEquals(1, entries.size());
        assertEquals("Real Heading", entries.get(0).title);
    }

    @Test
    public void parse_tildeFenceCodeBlock_ignored() {
        String content = "~~~\n# Not a heading\n~~~\n# Real Heading";
        List<TocParser.TocEntry> entries = TocParser.parse(content);
        assertEquals(1, entries.size());
        assertEquals("Real Heading", entries.get(0).title);
    }

    @Test
    public void parse_nestedCodeBlocks_respected() {
        String content = "```\n# Inside code\n```\n# Outside\n```\n# Inside again\n```";
        List<TocParser.TocEntry> entries = TocParser.parse(content);
        assertEquals(1, entries.size());
        assertEquals("Outside", entries.get(0).title);
    }

    @Test
    public void parse_headingWithExtraSpaces_trimsTitle() {
        List<TocParser.TocEntry> entries = TocParser.parse("#   Hello   World  ");
        assertEquals(1, entries.size());
        assertEquals("Hello   World", entries.get(0).title);
    }

    @Test
    public void parse_headingTooDeep_ignored() {
        String content = "####### Too Deep\n# Valid";
        List<TocParser.TocEntry> entries = TocParser.parse(content);
        assertEquals(1, entries.size());
        assertEquals("Valid", entries.get(0).title);
    }

    @Test
    public void parse_charOffset_tracksCorrectly() {
        String content = "Line 1\n# Heading\nLine 3";
        List<TocParser.TocEntry> entries = TocParser.parse(content);
        assertEquals(1, entries.size());
        assertEquals(7, entries.get(0).charOffset); // After "Line 1\n"
    }

    @Test
    public void parse_lineIndex_tracksCorrectly() {
        String content = "Line 0\nLine 1\n# Heading\nLine 3";
        List<TocParser.TocEntry> entries = TocParser.parse(content);
        assertEquals(1, entries.size());
        assertEquals(2, entries.get(0).lineIndex);
    }

    @Test
    public void parse_headingWithoutSpace_notRecognized() {
        String content = "#Not a heading";
        List<TocParser.TocEntry> entries = TocParser.parse(content);
        assertTrue(entries.isEmpty());
    }
}
