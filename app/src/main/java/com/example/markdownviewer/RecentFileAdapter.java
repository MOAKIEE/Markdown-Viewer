package com.example.markdownviewer;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

public class RecentFileAdapter extends ListAdapter<RecentFilesManager.RecentEntry, RecentFileAdapter.ViewHolder> {

    private final OnRecentClickListener listener;

    private static final DiffUtil.ItemCallback<RecentFilesManager.RecentEntry> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<RecentFilesManager.RecentEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull RecentFilesManager.RecentEntry oldItem,
                                                  @NonNull RecentFilesManager.RecentEntry newItem) {
                    return oldItem.uri.equals(newItem.uri);
                }

                @Override
                public boolean areContentsTheSame(@NonNull RecentFilesManager.RecentEntry oldItem,
                                                   @NonNull RecentFilesManager.RecentEntry newItem) {
                    return Objects.equals(oldItem.name, newItem.name)
                            && oldItem.scrollY == newItem.scrollY;
                }
            };

    public RecentFileAdapter(OnRecentClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecentFilesManager.RecentEntry entry = getItem(position);
        holder.bind(entry);
        holder.itemView.setOnClickListener(v -> listener.onRecentClick(entry));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_file_name);
        }

        void bind(RecentFilesManager.RecentEntry entry) {
            tvName.setText(entry.name != null && !entry.name.isEmpty()
                    ? entry.name : itemView.getContext().getString(R.string.recent_files_untitled));
        }
    }

    public interface OnRecentClickListener {
        void onRecentClick(RecentFilesManager.RecentEntry entry);
    }
}
