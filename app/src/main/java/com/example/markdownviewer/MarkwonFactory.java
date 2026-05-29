package com.example.markdownviewer;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkwonFactory {

    private static final String SAFE_TAGS =
            "p|br|hr|div|span|pre|code|blockquote|"
                    + "h[1-6]|ul|ol|li|dl|dt|dd|"
                    + "a|img|figure|figcaption|"
                    + "b|strong|i|em|u|s|del|ins|mark|small|sub|sup|abbr|"
                    + "table|thead|tbody|tfoot|tr|th|td|col|colgroup|caption|"
                    + "kbd|samp|var|wbr";

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
            "<(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)([^>]*)>",
            Pattern.DOTALL);

    // HTML 注释、CDATA、DOCTYPE — 直接移除
    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile(
            "<!--.*?-->", Pattern.DOTALL);
    private static final Pattern HTML_CDATA_PATTERN = Pattern.compile(
            "<!\\[CDATA\\[.*?\\]\\]>", Pattern.DOTALL);
    private static final Pattern HTML_DOCTYPE_PATTERN = Pattern.compile(
            "<!DOCTYPE[^>]*>", Pattern.CASE_INSENSITIVE);

    // 事件处理器：支持原始形式和 HTML 实体编码（如 &#111;&#110; = on）
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
            "\\s+(?:on|&#(?:111|79);&#(?:110|78);|&#x(?:6f|4f);&#x(?:6e|4e);)[a-zA-Z]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]*)",
            Pattern.CASE_INSENSITIVE);

    // 危险 URL 协议：javascript, vbscript, data, file
    private static final Pattern DANGEROUS_URL_PATTERN = Pattern.compile(
            "(href|src|action|formaction|xlink:href|background)\\s*=\\s*"
                    + "(?:\"\\s*(?:javascript|vbscript|data|file)\\s*:([^\"]*)\""
                    + "|'\\s*(?:javascript|vbscript|data|file)\\s*:([^']*)'"
                    + "|\\s*(?:javascript|vbscript|data|file)\\s*:([^\\s>]\\S*))",
            Pattern.CASE_INSENSITIVE);

    // style 属性中的危险 CSS（expression, behavior, javascript, moz-binding）
    private static final Pattern DANGEROUS_STYLE_PATTERN = Pattern.compile(
            "\\s+style\\s*=\\s*(?:\"[^\"]*(?:expression|behavior|javascript|moz-binding)[^\"]*\""
                    + "|'[^']*(?:expression|behavior|javascript|moz-binding)[^']*'"
                    + "|[^\\s>]*(?:expression|behavior|javascript|moz-binding)[^\\s>]*)",
            Pattern.CASE_INSENSITIVE);

    // 危险属性：target, download, ping（保留 rel 用于 nofollow 等安全用途，但清理危险值）
    private static final Pattern DANGEROUS_ATTRS_PATTERN = Pattern.compile(
            "\\s+(?:target|download|ping)\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SAFE_TAG_NAMES = Pattern.compile(SAFE_TAGS, Pattern.CASE_INSENSITIVE);

    private static final Pattern LATEX_PATTERN = Pattern.compile(
            "\\$\\$|\\\\\\(|\\\\\\[");

    // 移除 script 和 style 标签及其内容
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile(
            "<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile(
            "<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

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

    public static String sanitizeHtml(String content) {
        if (content == null) return null;

        // 1. 先移除 script/style 标签及其内容、HTML 注释、CDATA、DOCTYPE
        String cleaned = SCRIPT_TAG_PATTERN.matcher(content).replaceAll("");
        cleaned = STYLE_TAG_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = HTML_COMMENT_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = HTML_CDATA_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = HTML_DOCTYPE_PATTERN.matcher(cleaned).replaceAll("");

        // 2. 逐标签白名单过滤
        Matcher matcher = HTML_TAG_PATTERN.matcher(cleaned);
        StringBuilder sb = new StringBuilder(cleaned.length());
        while (matcher.find()) {
            String tagName = matcher.group(2);
            if (SAFE_TAG_NAMES.matcher(tagName).matches()) {
                String prefix = matcher.group(1);
                if (!prefix.isEmpty()) {
                    // 结束标签：标准化为简单形式
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(
                            "</" + tagName.toLowerCase() + ">"));
                } else {
                    // 开始标签：清理危险属性
                    String attrs = matcher.group(3);
                    attrs = EVENT_HANDLER_PATTERN.matcher(attrs).replaceAll("");
                    attrs = DANGEROUS_STYLE_PATTERN.matcher(attrs).replaceAll("");
                    attrs = DANGEROUS_ATTRS_PATTERN.matcher(attrs).replaceAll("");
                    attrs = DANGEROUS_URL_PATTERN.matcher(attrs).replaceAll("$1=\"#blocked\"");
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(
                            "<" + tagName.toLowerCase() + attrs + ">"));
                }
            } else {
                // 非白名单标签：直接移除
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
