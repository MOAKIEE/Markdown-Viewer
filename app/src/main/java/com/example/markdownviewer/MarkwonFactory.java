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

public final class MarkwonFactory {

    private MarkwonFactory() {}

    public static Markwon create(Context context, TextView textView, int themeMode) {
        return create(context, textView, themeMode, false);
    }

    public static Markwon create(Context context, TextView textView, int themeMode, boolean needsLatex) {
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
            builder.usePlugin(JLatexMathPlugin.create(textView.getTextSize(), b ->
                    b.inlinesEnabled(true)));
        }

        return builder.build();
    }

    public static boolean contentNeedsLatex(String content) {
        if (content == null) return false;
        return content.contains("$$") || content.contains("\\(") || content.contains("\\[");
    }
}
