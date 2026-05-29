package com.example.markdownviewer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

public class FileAdapter extends ListAdapter<FileItem, FileAdapter.FileViewHolder> {

    private final OnFileClickListener listener;

    private static final DiffUtil.ItemCallback<FileItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<FileItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull FileItem oldItem, @NonNull FileItem newItem) {
                    return Objects.equals(oldItem.getDocumentId(), newItem.getDocumentId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull FileItem oldItem, @NonNull FileItem newItem) {
                    return oldItem.equals(newItem);
                }
            };

    public FileAdapter(OnFileClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem fileItem = getItem(position);
        holder.bind(fileItem);
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            listener.onFileClick(getItem(pos), pos);
        });
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        private final TextView fileNameText;
        private final TextView fileTypeText;
        private final ImageView fileIconImage;
        private final androidx.cardview.widget.CardView cardView;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (androidx.cardview.widget.CardView) itemView;
            fileNameText = itemView.findViewById(R.id.file_name);
            fileTypeText = itemView.findViewById(R.id.file_type);
            fileIconImage = itemView.findViewById(R.id.file_icon);
        }

        public void bind(FileItem fileItem) {
            fileNameText.setText(fileItem.getName());
            if (fileItem.isParent()) {
                fileIconImage.setImageResource(R.drawable.ic_back);
                fileIconImage.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.ios_text_primary));
                fileTypeText.setText(R.string.file_picker_back);
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.card_parent_tint));
            } else {
                fileIconImage.clearColorFilter();
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.ios_card_bg));
                if (fileItem.isDirectory()) {
                    fileIconImage.setImageResource(R.drawable.ic_folder_mini);
                    fileTypeText.setText(R.string.file_picker_folder);
                } else {
                    fileIconImage.setImageResource(R.drawable.ic_file_md);
                    fileTypeText.setText(R.string.file_picker_md_file);
                }
            }
        }
    }

    public interface OnFileClickListener {
        void onFileClick(FileItem fileItem, int position);
    }
}
