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
            "p|br|hr|div|span|pre|code|blockquote|details|summary|"
                    + "h[1-6]|ul|ol|li|dl|dt|dd|"
                    + "a|img|figure|figcaption|picture|source|"
                    + "b|strong|i|em|u|s|del|ins|mark|small|sub|sup|abbr|"
                    + "table|thead|tbody|tfoot|tr|th|td|col|colgroup|caption|"
                    + "kbd|samp|var|ruby|rt|rp|wbr";

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
            "<(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)([^>]*)>",
            Pattern.DOTALL);

    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
            "\\s+on[a-zA-Z]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DANGEROUS_URL_PATTERN = Pattern.compile(
            "(href|src|action|formaction|xlink:href)\\s*=\\s*"
                    + "(?:\"\\s*(?:javascript|vbscript|data)\\s*:[^\"]*\""
                    + "|'\\s*(?:javascript|vbscript|data)\\s*:[^']*'"
                    + "|\\s*(?:javascript|vbscript|data)\\s*:[^\\s>]*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SAFE_TAG_NAMES = Pattern.compile(SAFE_TAGS, Pattern.CASE_INSENSITIVE);

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
        return content.contains("$$") || content.contains("\\(") || content.contains("\\[");
    }

    public static String sanitizeHtml(String content) {
        if (content == null) return null;
        Matcher matcher = HTML_TAG_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder(content.length());
        while (matcher.find()) {
            String tagName = matcher.group(2);
            if (SAFE_TAG_NAMES.matcher(tagName).matches()) {
                String prefix = matcher.group(1);
                if (!prefix.isEmpty()) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(
                            "</" + tagName + ">"));
                } else {
                    String attrs = EVENT_HANDLER_PATTERN.matcher(matcher.group(3)).replaceAll("");
                    attrs = DANGEROUS_URL_PATTERN.matcher(attrs).replaceAll("$1=\"#blocked\"");
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(
                            "<" + tagName + attrs + ">"));
                }
            } else {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
