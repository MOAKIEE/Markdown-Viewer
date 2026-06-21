package com.example.markdownviewer;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MarkwonFactory {

    private static final Pattern LATEX_PATTERN = Pattern.compile(
            "\\$\\$|\\\\\\(|\\\\\\[");
    private static final Pattern HTML_URL_ATTR_PATTERN = Pattern.compile(
            "<(?:a|img)\\b[^>]*(?:href|src)\\s*=",
            Pattern.CASE_INSENSITIVE);

    /**
     * jsoup Safelist 配置：只允许安全的 HTML 标签和属性。
     * 对比手写正则，jsoup 能正确解析 HTML 结构，不受实体编码、注释、嵌套等绕过技术影响。
     * 注意：不限制 URL 协议，危险 URL（javascript: 等）在 {@link #sanitizeHtml} 的
     * 后处理步骤中通过 {@link #sanitizeDangerousUrls} 清理。
     */
    private static final Safelist MARKDOWN_SAFE_LIST = Safelist.none()
            // 基础文本标签
            .addTags("p", "br", "hr", "div", "span", "pre", "code", "blockquote")
            // 标题
            .addTags("h1", "h2", "h3", "h4", "h5", "h6")
            // 列表
            .addTags("ul", "ol", "li", "dl", "dt", "dd")
            // 链接和图片
            .addTags("a", "img", "figure", "figcaption")
            // 文本格式化
            .addTags("b", "strong", "i", "em", "u", "s", "del", "ins", "mark", "small", "sub", "sup", "abbr")
            // 表格
            .addTags("table", "thead", "tbody", "tfoot", "tr", "th", "td", "col", "colgroup", "caption")
            // 其他
            .addTags("kbd", "samp", "var", "wbr")
            // 允许的 a 标签属性
            .addAttributes("a", "href")
            // 允许的 img 标签属性
            .addAttributes("img", "src", "alt", "title", "width", "height");

    private MarkwonFactory() {}

    public static Markwon create(Context context, TextView textView, int themeMode) {
        return create(context, textView.getTextSize(), themeMode, false);
    }

    public static Markwon create(Context context, TextView textView, int themeMode, boolean needsLatex) {
        return create(context, textView.getTextSize(), themeMode, needsLatex);
    }

    public static Markwon create(Context context, float textSizePx, int themeMode, boolean needsLatex) {
        int codeBg = ReaderTheme.getCodeBg(context, themeMode);
        int codeBlockBg = ReaderTheme.getCodeBlockBg(context, themeMode);
        int blockQuoteColor = ReaderTheme.getBlockQuoteColor(context, themeMode);
        int linkColor = ReaderTheme.getLinkColor(context, themeMode);

        Markwon.Builder builder = Markwon.builder(context)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(GlideImagesPlugin.create(context))
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.codeBackgroundColor(codeBg);
                        builder.codeBlockBackgroundColor(codeBlockBg);
                        builder.blockQuoteWidth(12);
                        builder.blockQuoteColor(blockQuoteColor);
                        builder.linkColor(linkColor);
                    }
                });

        if (needsLatex) {
            builder.usePlugin(JLatexMathPlugin.create(textSizePx, b ->
                    b.inlinesEnabled(true)));
        }

        return builder.build();
    }

    public static boolean contentNeedsLatex(String content) {
        if (content == null) return false;
        return LATEX_PATTERN.matcher(content).find();
    }

    /**
     * 使用 jsoup 进行可靠的 HTML 清理。
     *
     * <p>与手写正则相比的优势：
     * <ul>
     *   <li>正确解析 HTML 树结构，不受实体编码绕过影响</li>
     *   <li>自动移除所有不在白名单中的标签</li>
     *   <li>自动过滤危险属性（on* 事件处理器、javascript: URL 等）</li>
     *   <li>正确处理注释、CDATA、DOCTYPE、嵌套标签</li>
     * </ul>
     *
     * @param content 原始 Markdown 内容（可能包含内嵌 HTML）
     * @return 清理后的安全 HTML
     */
    public static String sanitizeHtml(String content) {
        if (content == null) return null;

        List<ProtectedMarkdownSegment> protectedSegments = new ArrayList<>();
        String protectedContent = protectMarkdownCode(content, protectedSegments);

        // 1. 使用 jsoup 的 Safelist 进行白名单过滤
        String cleaned = Jsoup.clean(protectedContent, "", MARKDOWN_SAFE_LIST,
                new Document.OutputSettings().prettyPrint(false));

        // 2. 后处理：清理危险 URL 协议（javascript:, vbscript:, data:text/html 等）
        cleaned = sanitizeDangerousUrls(cleaned);

        return restoreProtectedMarkdown(cleaned, protectedSegments);
    }

    private static String protectMarkdownCode(String content, List<ProtectedMarkdownSegment> segments) {
        String tokenPrefix = uniqueTokenPrefix(content);
        String fencedProtected = protectFencedCodeBlocks(content, segments, tokenPrefix);
        String indentedProtected = protectIndentedCodeBlocks(fencedProtected, segments, tokenPrefix);
        return protectInlineCode(indentedProtected, segments, tokenPrefix);
    }

    private static String uniqueTokenPrefix(String content) {
        String prefix = "@@MARKDOWN_VIEWER_PROTECTED_";
        while (content.contains(prefix)) {
            prefix += "X";
        }
        return prefix;
    }

    private static String protectFencedCodeBlocks(String content,
                                                  List<ProtectedMarkdownSegment> segments,
                                                  String tokenPrefix) {
        StringBuilder output = new StringBuilder(content.length());
        int index = 0;
        while (index < content.length()) {
            int lineEnd = findLineEnd(content, index);
            String line = content.substring(index, lineEnd);
            FenceMarker marker = parseFenceMarker(line);
            if (marker == null) {
                output.append(content, index, lineEndWithBreak(content, lineEnd));
                index = lineEndWithBreak(content, lineEnd);
                continue;
            }

            int blockStart = index;
            index = lineEndWithBreak(content, lineEnd);
            while (index < content.length()) {
                int closeLineEnd = findLineEnd(content, index);
                String closeLine = content.substring(index, closeLineEnd);
                index = lineEndWithBreak(content, closeLineEnd);
                if (isClosingFence(closeLine, marker)) {
                    break;
                }
            }

            output.append(addProtectedSegment(content.substring(blockStart, index), segments, tokenPrefix));
        }
        return output.toString();
    }

    private static String protectInlineCode(String content,
                                            List<ProtectedMarkdownSegment> segments,
                                            String tokenPrefix) {
        StringBuilder output = new StringBuilder(content.length());
        int index = 0;
        while (index < content.length()) {
            char c = content.charAt(index);
            if (c != '`') {
                output.append(c);
                index++;
                continue;
            }

            int tickCount = countRepeated(content, index, '`');
            String delimiter = repeat('`', tickCount);
            int close = content.indexOf(delimiter, index + tickCount);
            if (close < 0) {
                output.append(c);
                index++;
                continue;
            }

            int end = close + tickCount;
            output.append(addProtectedSegment(content.substring(index, end), segments, tokenPrefix));
            index = end;
        }
        return output.toString();
    }

    private static String protectIndentedCodeBlocks(String content,
                                                    List<ProtectedMarkdownSegment> segments,
                                                    String tokenPrefix) {
        StringBuilder output = new StringBuilder(content.length());
        int index = 0;
        boolean previousLineBlank = true;

        while (index < content.length()) {
            int lineEnd = findLineEnd(content, index);
            String line = content.substring(index, lineEnd);

            if (previousLineBlank && isIndentedCodeLine(line)) {
                int blockStart = index;
                int scan = lineEndWithBreak(content, lineEnd);
                boolean blockEndsWithBlank = false;

                while (scan < content.length()) {
                    int nextLineEnd = findLineEnd(content, scan);
                    String nextLine = content.substring(scan, nextLineEnd);
                    if (!isBlankLine(nextLine) && !isIndentedCodeLine(nextLine)) {
                        break;
                    }
                    blockEndsWithBlank = isBlankLine(nextLine);
                    scan = lineEndWithBreak(content, nextLineEnd);
                }

                output.append(addProtectedSegment(content.substring(blockStart, scan), segments, tokenPrefix));
                previousLineBlank = blockEndsWithBlank;
                index = scan;
                continue;
            }

            output.append(content, index, lineEndWithBreak(content, lineEnd));
            previousLineBlank = isBlankLine(line);
            index = lineEndWithBreak(content, lineEnd);
        }

        return output.toString();
    }

    private static String addProtectedSegment(String value,
                                             List<ProtectedMarkdownSegment> segments,
                                             String tokenPrefix) {
        String token = tokenPrefix + segments.size() + "@@";
        segments.add(new ProtectedMarkdownSegment(token, value));
        return token;
    }

    private static String restoreProtectedMarkdown(String content, List<ProtectedMarkdownSegment> segments) {
        String restored = content;
        for (ProtectedMarkdownSegment segment : segments) {
            restored = restored.replace(segment.token, segment.value);
        }
        return restored;
    }

    private static int findLineEnd(String content, int start) {
        int index = start;
        while (index < content.length()) {
            char c = content.charAt(index);
            if (c == '\n' || c == '\r') {
                break;
            }
            index++;
        }
        return index;
    }

    private static int lineEndWithBreak(String content, int lineEnd) {
        if (lineEnd >= content.length()) return lineEnd;
        if (content.charAt(lineEnd) == '\r'
                && lineEnd + 1 < content.length()
                && content.charAt(lineEnd + 1) == '\n') {
            return lineEnd + 2;
        }
        return lineEnd + 1;
    }

    private static FenceMarker parseFenceMarker(String line) {
        int index = 0;
        while (index < line.length() && index < 3 && line.charAt(index) == ' ') {
            index++;
        }
        if (index >= line.length()) return null;
        char marker = line.charAt(index);
        if (marker != '`' && marker != '~') return null;

        int count = countRepeated(line, index, marker);
        if (count < 3) return null;
        return new FenceMarker(marker, count);
    }

    private static boolean isClosingFence(String line, FenceMarker marker) {
        int index = 0;
        while (index < line.length() && index < 3 && line.charAt(index) == ' ') {
            index++;
        }
        if (index >= line.length() || line.charAt(index) != marker.marker) return false;
        int count = countRepeated(line, index, marker.marker);
        if (count < marker.length) return false;

        for (int i = index + count; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIndentedCodeLine(String line) {
        if (isBlankLine(line)) return false;

        int columns = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                columns++;
            } else if (c == '\t') {
                return true;
            } else {
                return columns >= 4;
            }

            if (columns >= 4) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlankLine(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int countRepeated(String text, int start, char c) {
        int index = start;
        while (index < text.length() && text.charAt(index) == c) {
            index++;
        }
        return index - start;
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private static final class ProtectedMarkdownSegment {
        final String token;
        final String value;

        ProtectedMarkdownSegment(String token, String value) {
            this.token = token;
            this.value = value;
        }
    }

    private static final class FenceMarker {
        final char marker;
        final int length;

        FenceMarker(char marker, int length) {
            this.marker = marker;
            this.length = length;
        }
    }

    /**
     * 后处理：清理危险的 URL（javascript:, vbscript:, data:text/html 等）。
     * jsoup Safelist 已移除了所有 on* 事件处理器，但 href/src 中的危险协议需要额外清理。
     *
     * <p>同时拦截 {@code data:image/svg+xml} —— SVG 内嵌可执行脚本，是 data: URL
     * 漏洞的真实载体，必须与 {@code data:text/html} 一并拦截。
     */
    private static String sanitizeDangerousUrls(String html) {
        if (html == null) return html;
        if (!HTML_URL_ATTR_PATTERN.matcher(html).find()) return html;

        org.jsoup.nodes.Document doc = Jsoup.parseBodyFragment(html);

        // 清理 a[href] 中的危险协议
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href").trim().toLowerCase(Locale.ROOT);
            if (href.startsWith("javascript:") ||
                href.startsWith("vbscript:") ||
                href.startsWith("data:")) {
                a.removeAttr("href");
            }
        }

        // 清理 img[src] 中的危险协议
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src").trim().toLowerCase(Locale.ROOT);
            if (src.startsWith("javascript:") ||
                src.startsWith("vbscript:") ||
                src.startsWith("data:text/html") ||
                src.startsWith("data:text/javascript") ||
                src.startsWith("data:application/javascript") ||
                src.startsWith("data:application/x-javascript") ||
                src.startsWith("data:application/xhtml+xml") ||
                src.startsWith("data:image/svg+xml")) {
                img.removeAttr("src");
            }
        }

        String result = doc.body().html();
        return result.isEmpty() ? html : result;
    }
}
