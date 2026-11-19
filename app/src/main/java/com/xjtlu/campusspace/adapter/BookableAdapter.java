package com.xjtlu.campusspace.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.ui.widget.Pills;

import java.util.ArrayList;
import java.util.List;

/** 可预约空间列表（讨论室 / 静音仓 / 图书馆区域） */
public class BookableAdapter extends RecyclerView.Adapter<BookableAdapter.VH> {

    public interface OnClick {
        void onClick(BookableVM vm);
    }

    private final List<BookableVM> items = new ArrayList<>();
    private final OnClick onClick;

    public BookableAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void setItems(List<BookableVM> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bookable, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        BookableVM vm = items.get(position);
        h.title.setText(vm.title);
        h.sub.setText(vm.subtitle);
        h.stat.setText(vm.statText);
        h.stat.setTextColor(vm.statColor);
        Pills.applySoft(h.tag, 0xFFE6EEFF);
        h.tag.setText(vm.tag);

        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(vm);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView tag;
        final TextView sub;
        final TextView stat;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_title);
            tag = v.findViewById(R.id.tv_tag);
            sub = v.findViewById(R.id.tv_sub);
            stat = v.findViewById(R.id.tv_stat);
        }
    }
}
