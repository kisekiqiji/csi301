package com.xjtlu.campusspace.adapter;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.core.TimeText;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.ui.widget.Pills;
import com.xjtlu.campusspace.util.ImageStore;

import java.util.ArrayList;
import java.util.List;

/** 纸条列表 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.VH> {

    private final List<Note> items = new ArrayList<>();
    private final boolean showThumb;

    public NoteAdapter() {
        this(true);
    }

    public NoteAdapter(boolean showThumb) {
        this.showThumb = showThumb;
    }

    public void setItems(List<Note> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Note n = items.get(position);

        int color;
        if (Note.C_LOST.equals(n.category)) {
            color = 0xFFFFF1D6;
        } else if (Note.C_REPAIR.equals(n.category)) {
            color = 0xFFFFE0E0;
        } else {
            color = 0xFFE6EEFF;
        }
        Pills.applySoft(h.category, color);
        h.category.setText(n.category);

        h.nick.setText(n.nickname);
        h.time.setText(TimeText.ago(n.createdAt));
        h.content.setText(n.content);

        String firstImage = n.imagePaths != null && !n.imagePaths.isEmpty() ? n.imagePaths.get(0) : n.imagePath;
        h.thumb.setOnClickListener(null);
        h.thumb.setImageDrawable(null);
        if (showThumb && firstImage != null) {
            Bitmap bmp = ImageStore.loadThumb(firstImage, 240);
            if (bmp != null) {
                h.thumb.setImageBitmap(bmp);
                h.thumb.setVisibility(View.VISIBLE);
                int count = n.imagePaths == null || n.imagePaths.isEmpty() ? 1 : n.imagePaths.size();
                h.thumb.setContentDescription("纸条图片，共 " + count + " 张，点击查看");
                h.thumb.setOnClickListener(v -> {
                    List<String> paths = new ArrayList<>();
                    if (n.imagePaths != null && !n.imagePaths.isEmpty()) paths.addAll(n.imagePaths);
                    else paths.add(firstImage);
                    String[] labels = new String[paths.size()];
                    for (int i = 0; i < labels.length; i++) labels[i] = "图片 " + (i + 1);
                    new AlertDialog.Builder(v.getContext()).setTitle("纸条图片")
                            .setItems(labels, (dialog, which) -> {
                                ImageView image = new ImageView(v.getContext());
                                image.setAdjustViewBounds(true);
                                image.setImageBitmap(ImageStore.loadThumb(paths.get(which), 1000));
                                new AlertDialog.Builder(v.getContext()).setView(image).setPositiveButton("关闭", null).show();
                            }).show();
                });
            } else {
                h.thumb.setVisibility(View.GONE);
            }
        } else {
            h.thumb.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView category;
        final TextView nick;
        final TextView time;
        final TextView content;
        final ImageView thumb;

        VH(@NonNull View v) {
            super(v);
            category = v.findViewById(R.id.tv_category);
            nick = v.findViewById(R.id.tv_nick);
            time = v.findViewById(R.id.tv_time);
            content = v.findViewById(R.id.tv_content);
            thumb = v.findViewById(R.id.iv_thumb);
        }
    }
}
