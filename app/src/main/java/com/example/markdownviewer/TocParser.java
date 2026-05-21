package com.example.markdownviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TocParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    private TocParser() {}

    public static List<TocEntry> parse(String content) {
        List<TocEntry> entries = new ArrayList<>();
        if (content == null) return entries;

        String[] lines = content.split("\n");
        boolean inCodeBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) continue;

            Matcher m = HEADING_PATTERN.matcher(trimmed);
            if (m.find()) {
                int level = m.group(1).length();
                String title = m.group(2).trim();
                entries.add(new TocEntry(title, level, i));
            }
        }
        return entries;
    }

    public static class TocEntry {
        public final String title;
        public final int level;
        public final int lineIndex;

        public TocEntry(String title, int level, int lineIndex) {
            this.title = title;
            this.level = level;
            this.lineIndex = lineIndex;
        }
    }
}
