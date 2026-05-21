package com.example.markdownviewer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private List<FileItem> files = new ArrayList<>();
    private OnFileClickListener listener;

    public FileAdapter(OnFileClickListener listener) {
        this.listener = listener;
    }

    public void setFiles(List<FileItem> newFiles) {
        List<FileItem> oldFiles = new ArrayList<>(files);
        files = newFiles;

        int oldSize = oldFiles.size();
        int newSize = newFiles.size();

        if (oldSize == 0 && newSize > 0) {
            notifyItemRangeInserted(0, newSize);
        } else if (newSize == 0 && oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize);
        } else {
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem fileItem = files.get(position);
        holder.bind(fileItem);
        holder.itemView.setOnClickListener(v -> listener.onFileClick(fileItem, holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        private TextView fileNameText;
        private TextView fileTypeText;
        private ImageView fileIconImage;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameText = itemView.findViewById(R.id.file_name);
            fileTypeText = itemView.findViewById(R.id.file_type);
            fileIconImage = itemView.findViewById(R.id.file_icon);
        }
        public void bind(FileItem fileItem) {
            fileNameText.setText(fileItem.getName());
            androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) itemView;
            if (fileItem.isParent()) {
                fileIconImage.setImageResource(R.drawable.ic_back);
                fileTypeText.setText(R.string.file_picker_back);
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.card_parent_tint));
            } else {
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
