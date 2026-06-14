package com.example.markdownviewer;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.List;

/**
 * 目录（Table of Contents）列表适配器。
 *
 * <p>从 {@code MarkdownActivity} 抽离，仅做位置不变的重构。
 * 按标题层级渲染缩进、字号与颜色：H1 加粗，H2 普通，H3+ 弱化。
 */
public class TocAdapter extends BaseAdapter {

    private final float density;
    private final List<TocParser.TocEntry> entries;

    public TocAdapter(Context context, List<TocParser.TocEntry> entries) {
        this.density = context.getResources().getDisplayMetrics().density;
        this.entries = entries;
    }

    @Override
    public int getCount() { return entries.size(); }

    @Override
    public Object getItem(int position) { return entries.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_toc, parent, false);
            holder = new ViewHolder();
            holder.tvTitle = convertView.findViewById(R.id.tv_toc_title);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        TocParser.TocEntry entry = entries.get(position);

        int paddingLeftPx = (int) (((entry.level - 1) * 16 + 8) * density + 0.5f);
        int paddingTopBottomPx = (int) (12 * density + 0.5f);
        int paddingRightPx = (int) (8 * density + 0.5f);
        convertView.setPadding(paddingLeftPx, paddingTopBottomPx, paddingRightPx, paddingTopBottomPx);

        if (entry.level == 1) {
            holder.tvTitle.setTextSize(16.5f);
            holder.tvTitle.setTypeface(null, Typeface.BOLD);
            holder.tvTitle.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.ios_text_primary));
        } else if (entry.level == 2) {
            holder.tvTitle.setTextSize(14.5f);
            holder.tvTitle.setTypeface(null, Typeface.NORMAL);
            holder.tvTitle.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.ios_text_primary));
        } else {
            holder.tvTitle.setTextSize(13.0f);
            holder.tvTitle.setTypeface(null, Typeface.NORMAL);
            holder.tvTitle.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.ios_text_secondary));
        }

        holder.tvTitle.setText(entry.title);
        return convertView;
    }

    static class ViewHolder {
        TextView tvTitle;
    }
}
