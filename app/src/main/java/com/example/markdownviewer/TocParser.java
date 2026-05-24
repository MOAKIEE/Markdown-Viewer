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

        boolean inCodeBlock = false;
        int lineIndex = 0;
        int lineStart = 0;
        int len = content.length();

        while (lineStart <= len) {
            int lineEnd = content.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = len;

            String line = content.substring(lineStart, lineEnd).trim();

            if (isFence(line)) {
                inCodeBlock = !inCodeBlock;
            } else if (!inCodeBlock) {
                Matcher m = HEADING_PATTERN.matcher(line);
                if (m.find()) {
                    int level = m.group(1).length();
                    String title = m.group(2).trim();
                    entries.add(new TocEntry(title, level, lineIndex, lineStart));
                }
            }

            lineStart = lineEnd + 1;
            lineIndex++;
        }
        return entries;
    }

    private static boolean isFence(String trimmedLine) {
        if (trimmedLine.length() < 3) return false;
        char c = trimmedLine.charAt(0);
        if (c != '`' && c != '~') return false;
        int count = 0;
        for (int i = 0; i < trimmedLine.length(); i++) {
            if (trimmedLine.charAt(i) == c) count++;
            else break;
        }
        return count >= 3;
    }

    public static class TocEntry {
        public final String title;
        public final int level;
        public final int lineIndex;
        public final int charOffset;

        public TocEntry(String title, int level, int lineIndex, int charOffset) {
            this.title = title;
            this.level = level;
            this.lineIndex = lineIndex;
            this.charOffset = charOffset;
        }
    }
}
