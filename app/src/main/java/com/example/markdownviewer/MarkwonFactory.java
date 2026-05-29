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

import java.util.regex.Pattern;

public final class MarkwonFactory {

    private static final Pattern LATEX_PATTERN = Pattern.compile(
            "\\$\\$|\\\\\\(|\\\\\\[");

    /**
     * jsoup Safelist 配置：只允许安全的 HTML 标签和属性。
     * 对比手写正则，jsoup 能正确解析 HTML 结构，不受实体编码、注释、嵌套等绕过技术影响。
     */
    /**
     * jsoup Safelist 配置：只允许安全的 HTML 标签和属性。
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

        // 1. 使用 jsoup 的 Safelist 进行白名单过滤
        String cleaned = Jsoup.clean(content, "", MARKDOWN_SAFE_LIST,
                new Document.OutputSettings().prettyPrint(false));

        // 2. 后处理：清理危险 URL 协议（javascript:, vbscript:, data:text/html 等）
        cleaned = sanitizeDangerousUrls(cleaned);

        return cleaned;
    }

    /**
     * 后处理：清理危险的 URL（javascript:, vbscript:, data:text/html 等）。
     * jsoup Safelist 已移除了所有 on* 事件处理器，但 href/src 中的危险协议需要额外清理。
     */
    private static String sanitizeDangerousUrls(String html) {
        if (html == null) return html;
        if (!html.contains(":")) return html;

        org.jsoup.nodes.Document doc = Jsoup.parseBodyFragment(html);

        // 清理 a[href] 中的危险协议
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href").trim().toLowerCase();
            if (href.startsWith("javascript:") ||
                href.startsWith("vbscript:") ||
                href.startsWith("data:")) {
                a.removeAttr("href");
            }
        }

        // 清理 img[src] 中的危险协议
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src").trim().toLowerCase();
            if (src.startsWith("javascript:") ||
                src.startsWith("vbscript:") ||
                src.startsWith("data:text/html") ||
                src.startsWith("data:text/javascript") ||
                src.startsWith("data:application/javascript") ||
                src.startsWith("data:application/x-javascript") ||
                src.startsWith("data:application/xhtml+xml")) {
                img.removeAttr("src");
            }
        }

        String result = doc.body().html();
        return result.isEmpty() ? html : result;
    }
}
